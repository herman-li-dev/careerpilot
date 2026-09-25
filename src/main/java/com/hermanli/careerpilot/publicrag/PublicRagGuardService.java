package com.hermanli.careerpilot.publicrag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "careerpilot.public-rag.guard.enabled", havingValue = "true")
public class PublicRagGuardService {

    private final PublicRagQuotaRepository quotaRepository;
    private final PublicRagGuardProperties properties;
    private final Clock clock;
    private final Semaphore concurrency;

    PublicRagGuardService(
            PublicRagQuotaRepository quotaRepository,
            PublicRagGuardProperties properties,
            PublicRagAuthProperties authProperties,
            Clock clock
    ) {
        if (!authProperties.isEnabled()) {
            throw new IllegalStateException("Public RAG authentication must be enabled before its guard.");
        }
        this.quotaRepository = quotaRepository;
        this.properties = properties;
        this.clock = clock.withZone(ZoneOffset.UTC);
        this.concurrency = new Semaphore(properties.getMaxConcurrentRequests(), true);
    }

    public GuardPermit acquire(String clerkSubject, List<String> promptParts, int requestedOutputTokens) {
        if (clerkSubject == null || clerkSubject.isBlank()) {
            throw new IllegalArgumentException("A verified Clerk subject is required.");
        }
        int estimatedInputTokens = estimateInputTokens(promptParts);
        validateTokenBudget(estimatedInputTokens, requestedOutputTokens);

        if (!concurrency.tryAcquire()) {
            throw rejected(PublicRagGuardRejectedException.Reason.CONCURRENCY_LIMIT);
        }

        LocalDate usageDate = LocalDate.now(clock);
        try {
            String principalKey = dailyPrincipalKey(usageDate, clerkSubject);
            PublicRagQuotaRepository.QuotaDecision decision = quotaRepository.reserve(
                    usageDate,
                    principalKey,
                    estimatedInputTokens,
                    requestedOutputTokens,
                    properties.getDailyUserRequestLimit(),
                    properties.getDailyGlobalRequestLimit()
            );
            if (decision == PublicRagQuotaRepository.QuotaDecision.USER_LIMIT_REACHED) {
                throw rejected(PublicRagGuardRejectedException.Reason.USER_DAILY_LIMIT);
            }
            if (decision == PublicRagQuotaRepository.QuotaDecision.GLOBAL_LIMIT_REACHED) {
                throw rejected(PublicRagGuardRejectedException.Reason.GLOBAL_DAILY_LIMIT);
            }
            return new GuardPermit(concurrency, usageDate, estimatedInputTokens, requestedOutputTokens);
        } catch (RuntimeException exception) {
            concurrency.release();
            throw exception;
        }
    }

    static int estimateInputTokens(List<String> promptParts) {
        if (promptParts == null || promptParts.isEmpty()) {
            return 0;
        }
        long utf8Bytes = 0;
        for (String part : promptParts) {
            if (part == null) {
                continue;
            }
            for (int offset = 0; offset < part.length();) {
                int codePoint = part.codePointAt(offset);
                utf8Bytes += utf8Length(codePoint);
                if (utf8Bytes > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                offset += Character.charCount(codePoint);
            }
        }
        return (int) utf8Bytes;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        if (codePoint <= 0xffff) {
            return 3;
        }
        return 4;
    }

    private void validateTokenBudget(int inputTokens, int outputTokens) {
        long totalTokens = (long) inputTokens + outputTokens;
        if (outputTokens <= 0
                || inputTokens > properties.getMaxInputTokens()
                || outputTokens > properties.getMaxOutputTokens()
                || totalTokens > properties.getMaxTotalTokens()) {
            throw rejected(PublicRagGuardRejectedException.Reason.TOKEN_LIMIT);
        }
    }

    private String dailyPrincipalKey(LocalDate usageDate, String clerkSubject) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getIdentityHmacSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            mac.update(usageDate.toString().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return HexFormat.of().formatHex(mac.doFinal(clerkSubject.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Public RAG identity protection is unavailable.", exception);
        }
    }

    private PublicRagGuardRejectedException rejected(PublicRagGuardRejectedException.Reason reason) {
        return new PublicRagGuardRejectedException(reason);
    }

    public static final class GuardPermit implements AutoCloseable {

        private final Semaphore concurrency;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final LocalDate usageDate;
        private final int estimatedInputTokens;
        private final int reservedOutputTokens;

        private GuardPermit(
                Semaphore concurrency,
                LocalDate usageDate,
                int estimatedInputTokens,
                int reservedOutputTokens
        ) {
            this.concurrency = concurrency;
            this.usageDate = usageDate;
            this.estimatedInputTokens = estimatedInputTokens;
            this.reservedOutputTokens = reservedOutputTokens;
        }

        public LocalDate usageDate() {
            return usageDate;
        }

        public int estimatedInputTokens() {
            return estimatedInputTokens;
        }

        public int reservedOutputTokens() {
            return reservedOutputTokens;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                concurrency.release();
            }
        }
    }
}
