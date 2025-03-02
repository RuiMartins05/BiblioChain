package org.example;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType
public record Publication(@Property String id, @Property String title) {

    public Publication(
            @JsonProperty("id") final String id,
            @JsonProperty("title") final String title
    ) {
        this.id = id;
        this.title = title;
    }

}
