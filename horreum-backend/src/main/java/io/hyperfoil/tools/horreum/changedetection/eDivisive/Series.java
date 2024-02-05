package io.hyperfoil.tools.horreum.changedetection.eDivisive;

/*
*
    Stores values of interesting metrics of all runs of
    a fallout test indexed by a single time variable.
    Provides utilities to analyze data e.g. find change points.
*/

import java.util.List;
import java.util.Map;

public class Series {
    public List<Integer> timeline;
    public Map<String, EDivisiveStructs.Metric> metrics;
    public Map<String, List<String>> attributes;
    public Map<String, List<Float>> data;


    public Series(List<Integer> timeline, Map<String, EDivisiveStructs.Metric> metrics, Map<String, List<String>> attributes, Map<String, List<Float>> data) {
        this.timeline = timeline;
        this.metrics = metrics;
        this.attributes = attributes;
        this.data = data;
//        assert all(len(x) == len(time) for x in data.values())
//        assert all(len(x) == len(time) for x in attributes.values())
    }

    public Map<String, String> attributes_at(Integer index){
        result = {}
        for (k, v) in self.attributes.items():
        result[k] = v[index]
        return result
    }

    def find_first_not_earlier_than(self, time: datetime) -> Optional[int]:
    timestamp = time.timestamp()
            for i, t in enumerate(self.time):
            if t >= timestamp:
            return i
        return None

    def find_by_attribute(self, name: str, value: str) -> List[int]:
            """Returns the indexes of data points with given attribute value"""
    result = []
            for i in range(len(self.time)):
            if self.attributes_at(i).get(name) == value:
            result.append(i)
            return result

    def analyze(self, options: AnalysisOptions = AnalysisOptions()) -> "AnalyzedSeries":
            logging.info(f"Computing change points for test {self.test_name}...")
            return AnalyzedSeries(self, options)

}
