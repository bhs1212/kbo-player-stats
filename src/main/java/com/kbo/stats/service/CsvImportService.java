package com.kbo.stats.service;

import com.kbo.stats.domain.Player;
import com.kbo.stats.domain.PlayerType;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * CSV 파일로부터 선수 데이터를 임포트하는 서비스
 *
 * CSV 컬럼 형식 (헤더 포함):
 * name,team,position,playerType,battingAvg,homeRuns,hits,rbi,era,wins,games
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final PlayerService playerService;

    public int importFromCsv(InputStream inputStream) {
        int count = 0;
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String[] header = reader.readNext(); // 헤더 스킵
            if (header == null) {
                log.warn("CSV 파일이 비어있습니다.");
                return 0;
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                try {
                    Player player = parseLine(line);
                    playerService.saveOrUpdate(player);
                    count++;
                } catch (Exception e) {
                    log.warn("CSV 행 파싱 실패: {} | 오류: {}", String.join(",", line), e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            log.error("CSV 임포트 실패: {}", e.getMessage());
            throw new RuntimeException("CSV 파일을 읽을 수 없습니다.", e);
        }

        log.info("CSV 임포트 완료: {}명", count);
        return count;
    }

    private Player parseLine(String[] cols) {
        // 컬럼: name,team,position,playerType,battingAvg,homeRuns,hits,rbi,era,wins,games
        if (cols.length < 11) {
            throw new IllegalArgumentException("컬럼 수 부족: " + cols.length);
        }

        return Player.builder()
                .name(cols[0].trim())
                .team(cols[1].trim())
                .position(cols[2].trim())
                .playerType(PlayerType.valueOf(cols[3].trim().toUpperCase()))
                .battingAvg(parseDouble(cols[4]))
                .homeRuns(parseInt(cols[5]))
                .hits(parseInt(cols[6]))
                .rbi(parseInt(cols[7]))
                .era(parseDouble(cols[8]))
                .wins(parseInt(cols[9]))
                .games(parseInt(cols[10]))
                .build();
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
