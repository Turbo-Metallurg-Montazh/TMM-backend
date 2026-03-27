package com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity;

import lombok.Data;

import java.util.Date;


@Data
public class FoundTendersArray {
    private Date fromDate;
    private Date toDate;
    private int tendersDownloadCount;
    private int totalPagesCount;
    private int loadedPagesCount;
    private FoundTenders foundTenders;

    public void setTotalPagesCount(int totalTendersCount, int itemsOnPage) {
        this.totalPagesCount = (int) Math.ceil((double) totalTendersCount / itemsOnPage);
    }
}
