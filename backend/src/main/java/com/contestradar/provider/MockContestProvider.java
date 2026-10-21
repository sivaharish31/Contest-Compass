package com.contestradar.provider;

import com.contestradar.contest.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(name = "contestradar.providers.mock-enabled", havingValue = "true")
public class MockContestProvider implements ContestProvider {

    private static final Logger log = LoggerFactory.getLogger(MockContestProvider.class);

    @Override
    public Platform platform() {
        return null;
    }

    @Override
    public List<ContestData> fetchContests() {
        log.info("[MOCK] Generating development fixture contests");
        Instant now = Instant.now();

        return List.of(
                new ContestData(
                        "mock-cf-900", Platform.CODEFORCES,
                        "[DEV] Codeforces Round 900 (Div. 2)",
                        now.plus(2, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS),
                        7200L,
                        "https://codeforces.com/contests",
                        "https://codeforces.com/contestRegistration/900",
                        null, 1900
                ),
                new ContestData(
                        "mock-lc-weekly-400", Platform.LEETCODE,
                        "[DEV] LeetCode Weekly Contest 400",
                        now.plus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS).plus(90, ChronoUnit.MINUTES),
                        5400L,
                        "https://leetcode.com/contest/weekly-contest-400",
                        null,
                        null, null
                ),
                new ContestData(
                        "mock-cc-starters-150", Platform.CODECHEF,
                        "[DEV] CodeChef Starters 150",
                        now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                        7200L,
                        "https://www.codechef.com/START150",
                        "https://www.codechef.com/START150",
                        null, null
                ),
                new ContestData(
                        "mock-ac-abc-370", Platform.ATCODER,
                        "[DEV] AtCoder Beginner Contest 370",
                        now.plus(5, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS).plus(100, ChronoUnit.MINUTES),
                        6000L,
                        "https://atcoder.jp/contests/abc370",
                        "https://atcoder.jp/contests/abc370",
                        null, 1999
                ),
                new ContestData(
                        "mock-cf-edu-170", Platform.CODEFORCES,
                        "[DEV] Educational Codeforces Round 170",
                        now.plus(12, ChronoUnit.HOURS), now.plus(12, ChronoUnit.HOURS).plus(2, ChronoUnit.HOURS),
                        7200L,
                        "https://codeforces.com/contests",
                        null,
                        null, 2100
                ),
                new ContestData(
                        "mock-lc-biweekly-135", Platform.LEETCODE,
                        "[DEV] LeetCode Biweekly Contest 135",
                        now.minus(30, ChronoUnit.MINUTES), now.plus(60, ChronoUnit.MINUTES),
                        5400L,
                        "https://leetcode.com/contest/biweekly-contest-135",
                        null,
                        null, null
                )
        );
    }
}
