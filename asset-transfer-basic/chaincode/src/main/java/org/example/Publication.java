package org.example;

import com.owlike.genson.annotation.JsonProperty;
import java.util.Objects;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType()
public class Publication {

    @Property()
    private String id;

    @Property()
    private String title;

    public Publication(
            @JsonProperty("id") final String id,
            @JsonProperty("title") final String title
    ) {
        this.id = id;
        this.title = title;
    }

    public String getId() {
        return id;
    }


    public String getTitle() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Publication that = (Publication) o;
        return Objects.equals(id, that.id) && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title);
    }

    @Override
    public String toString() {
        return "Publication{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
