package com.kbo.stats.dto;

import com.kbo.stats.domain.PlayerType;
import lombok.Data;

@Data
public class PlayerSearchDto {
    private String name;
    private String team;
    private String position;
    private PlayerType playerType;

    private int page = 1;
    private int size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}
