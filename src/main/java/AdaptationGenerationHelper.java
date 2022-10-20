import org.eclipse.jface.text.Document;

import java.util.HashMap;
import java.util.Map;

public class AdaptationGenerationHelper {
    String brokenLib = "jdt"; // todo
    String brokenClient = "";
    String brokenName = "";
    String brokenAPI = "";
    String brokenType= "";
    String brokenSubType = "";
    String brokenExamplePath = "1b.txt";
    String adaptationExamplePath = "1a.txt";
    String targetBrokenPath = "2b.txt";
    String targetAdaptationPath = "2a.txt";
    String adaptationType = "";
    int[] brokenPatternPositions;
    int[] adaptationPatternPositions;
    int[] targetBrokenPositions;
    int[] targetAdaptationPositions;
    String brokenExample;
    String adaptationExample;
    String targetBroken;
    String targetAdaptation;
    Document brokenPattern;
    Document adaptationPattern;
    Document targetBrokenPattern;
    Document targetAdaptationPattern;
    Document generatedAdaptation;
    Map<String,String> brokenPIdentifier2symbolic= new HashMap<>();
    Map<String,String> adaptationPIdentifier2symbolic= new HashMap<>();
    Map<String,String> targetBrokenIdentifier2symbolic= new HashMap<>();
    Map<String,String> targetAdaptationIdentifier2symbolic= new HashMap<>();
    Map<String,String> generatedAdaptationIdentifier2symbolic= new HashMap<>();
}

