-- 회원 정보 입력(FE /signup): 닉네임 · 출생 연도 · 성별 · 여행 관심사 · 약관 동의.
-- Google 로그인 때 users 행이 먼저 생기고, 회원 정보 입력을 마치면 profile_completed_at이 채워진다.

ALTER TABLE users
    ADD COLUMN nickname             VARCHAR(12),
    ADD COLUMN birth_year           INTEGER,
    ADD COLUMN gender               VARCHAR(16),
    ADD COLUMN profile_completed_at TIMESTAMPTZ;

-- 여행 관심사(최대 3개). 값은 TravelInterest enum 이름이다.
CREATE TABLE user_interests
(
    user_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    interest VARCHAR(32) NOT NULL,
    CONSTRAINT pk_user_interests PRIMARY KEY (user_id, interest)
);

-- 동의한 약관과 처음 동의한 시각. 동의하지 않았거나 동의를 거둔 약관은 행이 없다. 값은 TermsType enum 이름이다.
CREATE TABLE user_agreements
(
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    terms_type VARCHAR(32) NOT NULL,
    agreed_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_user_agreements PRIMARY KEY (user_id, terms_type)
);
