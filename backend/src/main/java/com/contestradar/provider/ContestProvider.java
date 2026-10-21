package com.contestradar.provider;

import com.contestradar.contest.Platform;

import java.util.List;

public interface ContestProvider {

    Platform platform();

    List<ContestData> fetchContests();
}
