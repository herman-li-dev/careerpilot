package com.hermanli.careerpilot.ai;

import com.hermanli.careerpilot.analysis.ReportGenerator;
import com.hermanli.careerpilot.documents.DocumentParser;
import com.hermanli.careerpilot.interview.InterviewQuestionGenerator;
import com.hermanli.careerpilot.plan.PlanGenerator;
import com.hermanli.careerpilot.review.ResumeReviewGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "careerpilot.ai.enabled=false",
        "spring.ai.dashscope.api-key=",
        "spring.ai.dashscope.chat.enabled=false",
        "spring.ai.dashscope.embedding.enabled=false",
        "spring.ai.dashscope.image.enabled=false",
        "spring.flyway.enabled=false"
})
class AiOptionalStartupTest {

    @Autowired
    private AiAvailability availability;

    @Autowired
    private DocumentParser documentParser;

    @Autowired
    private ReportGenerator reportGenerator;

    @Autowired
    private PlanGenerator planGenerator;

    @Autowired
    private InterviewQuestionGenerator interviewQuestionGenerator;

    @Autowired
    private ResumeReviewGenerator resumeReviewGenerator;

    @Test
    void startsWithoutDashScopeAndProvidesSafeOfflineBoundaries() {
        assertFalse(availability.isEnabled());
        assertThrows(AiUnavailableException.class, () -> documentParser.parseResume("Synthetic resume"));
        assertThrows(AiUnavailableException.class, () -> reportGenerator.generate("{}", "{}"));
        assertThrows(AiUnavailableException.class,
                () -> interviewQuestionGenerator.generate("{}"));
        assertThrows(AiUnavailableException.class,
                () -> resumeReviewGenerator.generate(null));
    }
}
