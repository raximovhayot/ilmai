package org.aiincubator.ilmai.ai.ingestion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
@ConfigurationProperties(prefix = "ingestion")
public class IngestionProperties {

    @NestedConfigurationProperty
    private PdfProps pdf = new PdfProps();

    @NestedConfigurationProperty
    private AudioProps audio = new AudioProps();
}
