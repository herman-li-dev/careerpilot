ALTER TABLE app_user
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN clerk_issuer VARCHAR(512),
    ADD COLUMN clerk_subject VARCHAR(255),
    ADD CONSTRAINT ck_app_user_clerk_identity_complete
        CHECK (
            (clerk_issuer IS NULL AND clerk_subject IS NULL)
            OR (
                clerk_issuer IS NOT NULL
                AND clerk_subject IS NOT NULL
                AND btrim(clerk_issuer) <> ''
                AND btrim(clerk_subject) <> ''
            )
        ),
    ADD CONSTRAINT ux_app_user_clerk_identity
        UNIQUE (clerk_issuer, clerk_subject);
