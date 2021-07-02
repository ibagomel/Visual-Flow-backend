package by.iba.vfapi.model.argo;

import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@EqualsAndHashCode
@NoArgsConstructor
@Getter
@Setter
@ToString
public class RuntimeData {
    private String startedAt;
    private String finishedAt;
    private String status;
    private double progress;
    private Map<String, String> jobsStatuses;

}