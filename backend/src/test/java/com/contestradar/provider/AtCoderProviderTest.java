package com.contestradar.provider;

import com.contestradar.contest.Platform;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtCoderProviderTest {

    private final AtCoderProvider provider = new AtCoderProvider(
            org.springframework.web.client.RestClient.builder());

    private static final String FIXTURE_HTML = """
            <html><body>
            <h3>Upcoming Contests</h3>
            <div class="panel panel-default"><div class="table-responsive">
            <table class="table"><thead><tr>
            <th>Start Time</th><th>Contest Name</th><th>Duration</th><th>Rated Range</th>
            </tr></thead><tbody>
            <tr>
            <td><a href='http://timeanddate.com'><time class="fixtime fixtime-full">2026-10-24 21:00:00+0900</time></a></td>
            <td><span><a href="/contests/abc481">ABC 481</a></span></td>
            <td class="text-center">01:40</td>
            <td>- 1999</td>
            </tr>
            <tr>
            <td><a href='http://timeanddate.com'><time class="fixtime fixtime-full">2026-10-25 21:00:00+0900</time></a></td>
            <td><span><a href="/contests/arc233">ARC 233</a></span></td>
            <td class="text-center">02:30</td>
            <td>1600 - 2999</td>
            </tr>
            <tr>
            <td><a href='http://timeanddate.com'><time class="fixtime fixtime-full">2026-10-30 19:00:00+0900</time></a></td>
            <td><span><a href="/contests/ahc074">AHC 074</a></span></td>
            <td class="text-center">240:00</td>
            <td>All</td>
            </tr>
            <tr>
            <td><a href='http://timeanddate.com'><time class="fixtime fixtime-full">2026-10-31 21:00:00+0900</time></a></td>
            <td><span><a href="/contests/abc482">ABC 482</a></span></td>
            <td class="text-center">01:40</td>
            <td>- 1999</td>
            </tr>
            <tr>
            <td><a href='http://timeanddate.com'><time class="fixtime fixtime-full">2026-11-07 21:00:00+0900</time></a></td>
            <td><span><a href="/contests/abc483">ABC 483</a></span></td>
            <td class="text-center">01:40</td>
            <td>- 1999</td>
            </tr>
            </tbody></table></div></div>
            <h3>Recent Contests</h3>
            <table><tbody><tr>
            <td><a href="/contests/old1">Old Contest</a></td>
            </tr></tbody></table>
            </body></html>
            """;

    @Test
    void parsesAllUpcomingContests() {
        List<ContestData> contests = provider.parseUpcomingContests(FIXTURE_HTML);
        assertEquals(5, contests.size());
    }

    @Test
    void parsesContestIdAndTitle() {
        List<ContestData> contests = provider.parseUpcomingContests(FIXTURE_HTML);
        ContestData first = contests.get(0);
        assertEquals("abc481", first.externalId());
        assertEquals("ABC 481", first.name());
        assertEquals(Platform.ATCODER, first.platform());
    }

    @Test
    void parsesStartTimeWithTimezone() {
        List<ContestData> contests = provider.parseUpcomingContests(FIXTURE_HTML);
        ContestData first = contests.get(0);
        // 2026-10-24 21:00:00+0900 = 2026-10-24T12:00:00Z
        Instant expected = ZonedDateTime.parse("2026-10-24 21:00:00+0900",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ")).toInstant();
        assertEquals(expected, first.startTime());
    }

    @Test
    void parsesDuration() {
        List<ContestData> contests = provider.parseUpcomingContests(FIXTURE_HTML);
        assertEquals(6000L, contests.get(0).durationSeconds()); // 01:40 = 6000s
        assertEquals(9000L, contests.get(1).durationSeconds()); // 02:30 = 9000s
        assertEquals(864000L, contests.get(2).durationSeconds()); // 240:00 = 864000s
    }

    @Test
    void computesEndTime() {
        List<ContestData> contests = provider.parseUpcomingContests(FIXTURE_HTML);
        ContestData first = contests.get(0);
        assertEquals(first.startTime().plusSeconds(6000), first.endTime());
    }

    @Test
    void constructsCorrectUrls() {
        List<ContestData> contests = provider.parseUpcomingContests(FIXTURE_HTML);
        assertEquals("https://atcoder.jp/contests/abc481", contests.get(0).url());
        assertEquals("https://atcoder.jp/contests/abc481", contests.get(0).registrationUrl());
    }

    @Test
    void stopsAtRecentContestsSection() {
        List<ContestData> contests = provider.parseUpcomingContests(FIXTURE_HTML);
        boolean hasOld = contests.stream().anyMatch(c -> c.name().contains("Old"));
        assertFalse(hasOld, "Should not include contests from Recent Contests section");
    }

    @Test
    void handlesEmptyUpcomingSection() {
        String html = "<html><body><h3>Upcoming Contests</h3><table></table><h3>Recent Contests</h3></body></html>";
        List<ContestData> contests = provider.parseUpcomingContests(html);
        assertTrue(contests.isEmpty());
    }

    @Test
    void handlesMissingUpcomingSection() {
        String html = "<html><body><h3>Recent Contests</h3></body></html>";
        List<ContestData> contests = provider.parseUpcomingContests(html);
        assertTrue(contests.isEmpty());
    }

    @Test
    void handlesMalformedTimeGracefully() {
        String html = """
                <html><body><h3>Upcoming Contests</h3><table><tbody>
                <tr>
                <td><time class="fixtime">not-a-date</time></td>
                <td><a href="/contests/bad1">Bad Contest</a></td>
                <td>01:40</td>
                </tr>
                <tr>
                <td><time class="fixtime">2026-10-24 21:00:00+0900</time></td>
                <td><a href="/contests/good1">Good Contest</a></td>
                <td class="text-center">01:40</td>
                </tr>
                </tbody></table><h3>Recent Contests</h3></body></html>
                """;
        List<ContestData> contests = provider.parseUpcomingContests(html);
        assertEquals(1, contests.size());
        assertEquals("good1", contests.get(0).externalId());
    }
}
