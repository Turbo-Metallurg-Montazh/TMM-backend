package com.kindred.emkcrm_project_backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kindred.emkcrm_project_backend.config.FormatProperties;
import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import com.kindred.emkcrm_project_backend.db.entities.UnloadingDate;
import com.kindred.emkcrm_project_backend.db.repositories.TenderFilterRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UnloadingDateRepository;
import com.kindred.emkcrm_project_backend.db.entities.foundTendersEntity.FoundTendersArray;
import com.kindred.emkcrm_project_backend.utils.FindTenders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@Slf4j
@Service
public class AutomaticTendersDownloader {

    private final FindTenders findTenders;
    private final UnloadingDateRepository unloadingDateRepository;
    private final TenderFilterRepository tenderFilterRepository;
    private final FormatProperties formatProperties;

    public AutomaticTendersDownloader(
            FindTenders findTenders,
            UnloadingDateRepository unloadingDateRepository,
            TenderFilterRepository tenderFilterRepository,
            FormatProperties formatProperties
            ) {
        this.findTenders = findTenders;
        this.unloadingDateRepository = unloadingDateRepository;
        this.tenderFilterRepository = tenderFilterRepository;
        this.formatProperties = formatProperties;
    }

    @Scheduled(cron = "${schedule.time}", zone = "Europe/Moscow") // запуск в 3:00 каждый день
    public void downloadNewTenders() {
        for (TenderFilter tenderFilter : tenderFilterRepository.findAllByActiveIs(true)) {
            long filterId = tenderFilter.getId();
            try {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatProperties.date_time());
                String formattedDateTimeTo = now.format(formatter);

                UnloadingDate unloadingDate = unloadingDateRepository.findTopByFilterIdOrderByUnloadDateDesc(filterId);
                if (unloadingDate == null) {
                    LocalDateTime weekAgo = LocalDateTime.now().minusYears(8);
                    unloadingDate = new UnloadingDate();
                    unloadingDate.setFilterId(filterId);
                    unloadingDate.setUnloadDate(weekAgo);
                }

                FoundTendersArray foundTendersArray = findTenders.findTenders(
                        tenderFilter,
                        unloadingDate.getUnloadDate().format(formatter),
                        formattedDateTimeTo,
                        1,
                        10
                );
                unloadingDateRepository.save(new UnloadingDate(filterId, now));

                log.info("Automatic download completed for filterId={}, loaded={}", filterId, foundTendersArray.getTendersDownloadCount());
            } catch (JsonProcessingException e) {
                log.error("Automatic download failed for filterId={}", filterId, e);
            }
        }
    }
}
