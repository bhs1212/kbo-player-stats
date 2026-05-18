package com.kbo.stats.mapper;

import com.kbo.stats.domain.StatValidationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StatValidationLogMapper {

    void insert(StatValidationLog log);

    List<StatValidationLog> findRecent(@Param("limit") int limit);

    Long countTotal();

    Long countMatched();

    Long countByMetric(@Param("metric") String metric);

    Long countMatchedByMetric(@Param("metric") String metric);
}
