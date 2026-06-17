package org.aiincubator.ilmai.gaps.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aiincubator.ilmai.gaps.GapTrend;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GapItem {

    private UUID id;
    private String concept;
    private int missCount;
    private int hitCount;
    private double accuracy;
    private OffsetDateTime lastSeenAt;
    private UUID suggestedMaterialId;
    private String suggestedMaterialName;
    private GapTrend trend;
}
