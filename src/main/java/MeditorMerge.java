import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MeditorMerge {
    static boolean isCombine = true;
    static String combination = "Transform"; // Delete,Copy,Transform
    static String dataset = "Development"; // Original,Development,Upgrade
    public static void main(String[] args) throws Exception {
        Util.zipUncompress(dataset+"_Source.zip",".");
        recurrentComputeSimilarity(dataset+"_Source/adaptation");
        logger.info("correctness "+correctNumber*1.0/number+" similarity "+sum/number+" notMatchNumber "+notMatchNumber);
    }

    public static Logger logger = LoggerFactory.getLogger(MeditorMerge.class);
    static double sum = 0;
    static int number = 0;
    static int correctNumber = 0;
    static int notMatchNumber = 0;
    static boolean findNotMatchG = false;
    static AdaptationGenerationHelper helper;
    public static void recurrentComputeSimilarity(String dirPath) {
        File dir = new File(dirPath);
        for(File f: Objects.requireNonNull(dir.listFiles())){
            if (f.isDirectory()){recurrentComputeSimilarity(f.getAbsolutePath());}
            else {
                for(File f2: Objects.requireNonNull(dir.listFiles())){
                    if (f2.isDirectory()){continue;}
                    else if(f.getName().equals(f2.getName())){continue;}
                    else{
                        try {
                            number +=1;
                            float similarity = parseJavaFile(f.getAbsolutePath(),f2.getAbsolutePath()); // A is reference adaptation, B is targeted broken API usage.
                            sum += similarity;
                            if (similarity>=0.99) correctNumber++;
                            logger.info("number "+number+" correctNumber "+correctNumber+" similarity "+similarity+" avgSim "+sum/number+" sum "+sum+" f1Path:"+f.getAbsolutePath()+" f2Path:"+f2.getAbsolutePath());
                        } catch (Exception e) {
                            e.printStackTrace();
                            ;
                        }
                        break;
                    }
                }
            }
        }
    }
    public static float parseJavaFile(String adaptationAPath,String adaptationBPath) throws Exception {
        String affectedAPath = adaptationAPath.replace("adaptation","affected");
        String affectedBPath = adaptationBPath.replace("adaptation","affected");

        findNotMatchG = false;
        helper = new AdaptationGenerationHelper();
        // setting target broken lib, client, and broken API usage lines
        helper.brokenLib = Util.getBrokenLibFromFilePath(affectedAPath)[0];
        helper.brokenClient = Util.getBrokenLibFromFilePath(affectedAPath)[1];
        helper.brokenType = Util.getBrokenAPIAndTypeFromFilePath(affectedAPath)[0];
        helper.brokenSubType = Util.getBrokenAPIAndTypeFromFilePath(affectedAPath)[1];
        helper.brokenAPI = Util.getBrokenAPIAndTypeFromFilePath(affectedAPath)[2];
        if (helper.brokenType.equals("Type")){
            helper.brokenName = helper.brokenAPI.split("")[0];
        }else {
            helper.brokenName = helper.brokenAPI.split("")[1];
        }
        helper.brokenExamplePath = affectedAPath;
        helper.adaptationExamplePath = adaptationAPath;
        helper.targetBrokenPath = affectedBPath;
        helper.targetAdaptationPath = adaptationBPath;

        // Positions  startPosition length startLine  lineLength
        helper.brokenPatternPositions = Util.getStartPositionAndLength(helper.brokenExamplePath);
        helper.adaptationPatternPositions = Util.getStartPositionAndLength(helper.adaptationExamplePath);
        helper.targetBrokenPositions = Util.getStartPositionAndLength(helper.targetBrokenPath);
        helper.targetAdaptationPositions = Util.getStartPositionAndLength(helper.targetAdaptationPath);
        helper.brokenExample = Util.getMRDocument(helper.brokenExamplePath).get();
        helper.adaptationExample = Util.getMRDocument(helper.adaptationExamplePath).get();
        helper.targetBroken = Util.getMRDocument(helper.targetBrokenPath).get();
        helper.targetAdaptation = Util.getMRDocument(helper.targetAdaptationPath).get();


        // return cu,document,brokenLib,brokenClient
        SymbolicPatternAndMap tmp;
        tmp = findLibraryIrrelevantIdentifier(helper.brokenLib,helper.brokenPatternPositions,helper.brokenExamplePath,"bp_");
        helper.brokenPattern = tmp.document;
        helper.brokenPIdentifier2symbolic = tmp.identifier2Symbolic;
        tmp = findLibraryIrrelevantIdentifier(helper.brokenLib,helper.adaptationPatternPositions,helper.adaptationExamplePath,"ap_");
        helper.adaptationPattern = tmp.document;
        helper.adaptationPIdentifier2symbolic = tmp.identifier2Symbolic;
        tmp = findLibraryIrrelevantIdentifier(helper.brokenLib,helper.targetBrokenPositions,helper.targetBrokenPath,"tb_");
        // second add broken API usage detect and hierarchical match. This is an novel improvement. And the second one is adaptation merge from ap and tb using bp. done but not good
        helper.targetBrokenPattern=tmp.document;
        helper.targetBrokenIdentifier2symbolic = tmp.identifier2Symbolic;

        logger.debug("brokenExample:\n"+helper.brokenExample);
        logger.debug("brokenPattern:\n"+helper.brokenPattern.get());
        logger.debug("targetBroken:\n"+helper.targetBroken);
        logger.debug("targetBrokenPattern:\n"+helper.targetBrokenPattern.get());
        logger.debug("adaptationExample:\n"+helper.adaptationExample);
        logger.debug("adaptationPattern:\n"+helper.adaptationPattern.get());
        logger.debug("targetAdaptation:\n"+helper.targetAdaptation);

        try {
//            helper.generatedAdaptation = symbolicMatchPatternAndGenerateAdaptationBySimpleName(helper.brokenPattern,helper.adaptationPattern,helper.targetBrokenPattern,helper);
            helper.generatedAdaptation = symbolicMatchPatternAndGenerateAdaptationByExpression(helper.brokenPattern,helper.adaptationPattern,helper.targetBrokenPattern,helper);
        }catch (Exception e){
            e.printStackTrace();
            return 0;
        }

        helper.generatedAdaptation = Util.syncGa(new Document(helper.targetAdaptation),helper.generatedAdaptation);
        Map<String, String> apSymbolic2Identifier = helper.adaptationPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
        Map<String, String> bpSymbolic2Identifier = helper.brokenPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
        Map<String, String> tbSymbolic2Identifier = helper.targetBrokenIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
        helper.generatedAdaptation = Util.rewriteSimpleName2(apSymbolic2Identifier,helper.generatedAdaptation);
        helper.generatedAdaptation = Util.rewriteSimpleName2(bpSymbolic2Identifier,helper.generatedAdaptation);
        helper.generatedAdaptation = Util.rewriteSimpleName2(tbSymbolic2Identifier,helper.generatedAdaptation);

        float result = Util.evaluationSimilarly(helper.generatedAdaptation.get(),helper.targetAdaptation);
        logger.debug("generatedAdaptation:\n"+helper.generatedAdaptation.get());

//        if (findNotMatchG &&result>=0.99){
//            logger.info("point api transform");
//        }

        if (findNotMatchG && !isCombine) return 0; // todo Complementary Setting

//        if (!findNotMatchG &&result<0.99){
//            logger.info("point");
//        }
        return result;
    }
    public static SymbolicPatternAndMap findLibraryIrrelevantIdentifier(String brokenLib, int[] patternPositions,String path,String pPrefix) throws IOException {
        // assumption: client code will import all libraries' facts explicitly rather than import xxx.*
        // anonymous API-irrelevant methods, variables, literals

        // get source code from file path
        String sourcecode = Util.getFileContentWithoutPartialPrefix(path);
        sourcecode = Util.completeBlock(sourcecode);
        Document document = new Document(sourcecode); // create document for re write
        ASTNode cu = Util.getASTNodeFromDocument(document);


        // find all broken lib classes
        ImportVisitor iv = new ImportVisitor();
        iv.brokenLib = brokenLib;
        cu.accept(iv);

        ASTNode subAstNode = NodeFinder.perform(cu,patternPositions[0],patternPositions[1]);
        // find all method declaration
        MethodDeclarationVisitor mdv = new MethodDeclarationVisitor();
        subAstNode.accept(mdv);

        // find all types
        SimpleTypeVisitor stv = new SimpleTypeVisitor();
        stv.libTName = iv.libTNames;
        subAstNode.accept(stv);
        logger.debug("t.anonymous: "+stv.userTName);

        // find all broken lib objects
        VariableDeclarationVisitor vdvflv = new VariableDeclarationVisitor();
        vdvflv.brokenLibClasses = iv.libTNames;
        subAstNode.accept(vdvflv);
        FieldAccessVisitor favffalf = new FieldAccessVisitor();
        favffalf.brokenLibClasses = iv.libTNames;
        favffalf.types = stv.tNames;
        favffalf.variables = vdvflv.libVNames;
        favffalf.brokenLibObject = vdvflv.libVNames;
        favffalf.importHeads=iv.headNames;
        subAstNode.accept(favffalf);

        // find all method invocations
        MethodInvocationVisitor miv = new MethodInvocationVisitor();
        miv.userMNames = mdv.userMName;
        miv.brokenLibClasses = iv.libTNames;
        miv.brokenLibObject = vdvflv.libVNames;
        subAstNode.accept(miv);
        logger.debug("m.anonymous: "+miv.userMQNames);

        // find all identifiers and all identifiers names that need anonymous

        SimpleNameVisitor snv = new SimpleNameVisitor();
        subAstNode.accept(snv);

        snv.vNames = Util.removeStringForList2(snv.vNames,iv.headNames); // replace set with list? yes! 方便后续的match，这样相同位置的变量会尽可能的编码为相同的Symbolic name done
        snv.vNames = Util.removeStringForList2(snv.vNames,iv.libTNames);
        snv.vNames = Util.removeStringForList2(snv.vNames,stv.tNames);
        snv.vNames = Util.removeStringForList2(snv.vNames,miv.mNames);
        snv.vNames = Util.removeStringForList2(snv.vNames,favffalf.libFNames); // todo Third Novel Idea
        snv.vNames = Util.removeStringForList2(snv.vNames,favffalf.userFName);
//        snv.names.removeAll(qnv.names); // review the correctness of this policy // 不妥，会禁掉所有的field access
        // assumption: the analyzed code should comply with java standard specification
        snv.vNames = Util.filterStartWithUppercase(snv.vNames);
        logger.debug("v.anonymous: "+snv.vNames);

        ImmediatelyVisitor imv = new ImmediatelyVisitor();
        subAstNode.accept(imv);
        logger.debug("c.anonymous: "+imv.namesL);

        // other ways to find libraries irrelevant identifier
        // 变量常见的位置，variable declaration， assignment left， parameters

        // return sv.anonymous miv.anonymous
//        String tmp = getPatternStatements(document,patternPositions);
        document = new Document(Util.getPatternStatements(document,patternPositions)); // extract migration related
        SymbolicPatternAndMap spam = new SymbolicPatternAndMap();
        spam.document = document;
        spam.identifier2Symbolic = new HashMap<>();
//        spam = getSymbolicMap(spam,stv.userTName,"t_"+pPrefix); // todo Second Novel Idea
        spam = Util.getSymbolicMap(spam,miv.userMQNames,"m_"+pPrefix);
        spam = Util.getSymbolicMap(spam,snv.vNames,"v_"+pPrefix);
        spam = Util.getSymbolicMap(spam,imv.namesL,"c_"+pPrefix);

        return spam;
    }

    // new Meditor
    public static Document symbolicMatchPatternAndGenerateAdaptationByExpression(Document brokenPattern, Document adaptationPattern, Document targetBroken, AdaptationGenerationHelper helper) throws Exception {
        // rewrite AST

        // get map from apSymbolic2bpSymbolic
        Map<String,String> apSymbolic2bpSymbolic = new HashMap<>();
        Map<String, String> apSymbolic2Identifier = helper.adaptationPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey())); // todo check is it influence other thing?
        for (String aps: apSymbolic2Identifier.keySet()){// cao, map 也是无序的！不过没关系
            String apI = apSymbolic2Identifier.get(aps);
            if (helper.brokenPIdentifier2symbolic.keySet().contains(apI)){
                String bps = helper.brokenPIdentifier2symbolic.get(apI);
                if (!aps.equals(bps)) apSymbolic2bpSymbolic.put(aps,bps);
            }
        }

        Document adaptationPatternNew = Util.rewriteSimpleName2(apSymbolic2bpSymbolic,adaptationPattern);
        logger.debug("generatedAdaptationMatch1:\n"+adaptationPatternNew.get());
//        System.out.println(adaptationPatternNew.get());

        Map<String,String> bpSymbolic2tbSymbolic = new HashMap<>();
        String[] bpl = Util.splitWordsForExpMatch(brokenPattern.get());
        String[] tbl = Util.splitWordsForExpMatch(targetBroken.get());
        List<String> original = Arrays.asList(bpl);
        List<String> revised = Arrays.asList(tbl);

        Patch<String> diff = DiffUtils.diff(original, revised);
        List<AbstractDelta<String>> deltas = diff.getDeltas();
        AtomicBoolean findNotMatch = new AtomicBoolean(false);
        deltas.forEach(delta -> {
            switch (delta.getType()) {
                case INSERT:
                    //新增
                    Chunk<String> insert = delta.getTarget();
                    logger.debug("INSERT: "+"+ " + (insert.getPosition() + 1) + " " + insert.getLines());
//                    notMatchNumber+=1;
                    findNotMatch.set(true);

//                    String tmp = String.join("",insert.getLines()).replaceAll("\\s*","");
//                    if (!tmp.equals("")) findNotMatch.set(true);
                    break;
                case CHANGE:
                    //修改
                    Chunk<String> source = delta.getSource();
                    Chunk<String> target1 = delta.getTarget();
                    String sS = String.join("",source.getLines());
                    String tS = String.join("", target1.getLines());
//                    String tmpSS = sS.replaceAll("\\s*","");
//                    String tmpTS = tS.replaceAll("\\s*","");
//                    String separator1 = "[ \t\n;:.()\\[\\]{}<>,+\\-*/=|&!]";
//                    tmpSS = Util.splitSentence(tmpSS,separator1).get(0);
//                    tmpTS = Util.splitSentence(tmpTS,separator1).get(0);
                    logger.debug("CHANGE: "+"\n- " + (source.getPosition() + 1) + " " + sS + "\n+ " + "" + (target1.getPosition() + 1) + " " + tS);

                    // Old Meditor
//                    if (tmpSS.startsWith("v_")||tmpSS.startsWith("t_")||tmpSS.startsWith("m_")||tmpSS.startsWith("c_")){
//                        if (tmpTS.startsWith("v_")||tmpTS.startsWith("t_")||tmpTS.startsWith("m_")||tmpTS.startsWith("c_")){
//                            System.out.println("else"); // Old Meditor
//                        } else {
//                            System.out.println("point");
//                        }
//                    } else {
//                        System.out.println("point");
//                    }
                    // end

                    // new Meditor
                    bpSymbolic2tbSymbolic.put(sS,tS); // todo First Novel Idea   todo check reasonable?
                    break;
                case DELETE:
                    //删除
                    Chunk<String> delete = delta.getSource();
                    String dS = String.join("",delete.getLines());
                    String tmpDS = dS.replaceAll("\\s*","");
                    logger.debug("DELETE: "+"- " + (delete.getPosition() + 1) + " " + delete.getLines());
//                    notMatchNumber+=1;
                    // new Meditor
//                    bpSymbolic2tbSymbolic.put(dS,"");
                    findNotMatch.set(true);
                    // old Meditor
//                    String tmp2 = String.join("",delete.getLines()).replaceAll("\\s*","");
//                    if (!tmp2.equals("")) findNotMatch.set(true);
                    break;
                case EQUAL:
                    System.out.println("no change");
                    break;
            }
        });
        if (findNotMatch.get()){
            notMatchNumber+=1;
            findNotMatchG = true;
            switch (combination) {
                case "Delete":
                    return new Document(""); // Directly Delete

                case "Copy":
                    return new Document(helper.adaptationExample);// Directly Delete

                case "Transform":
                    return APITransform.generateAdaptation(helper.adaptationExamplePath, helper.targetAdaptationPath, helper);// API Transform

            }
//            return new Document(helper.adaptationExample); // Directly Copy // todo Complementary Setting
//            return new Document(""); // Directly Delete
//            return APITransformV1.generateAdaptation(helper.adaptationExamplePath,helper.targetAdaptationPath,helper); // API Transform
        }
        adaptationPatternNew = Util.rewriteSimpleName2(bpSymbolic2tbSymbolic,adaptationPatternNew);
        logger.debug("generatedAdaptationMatch2:\n"+adaptationPatternNew.get());
        Map<String, String> tbSymbolic2Identifier = helper.targetBrokenIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
        adaptationPatternNew = Util.rewriteSimpleName2(tbSymbolic2Identifier,adaptationPatternNew);

        // generate target adaptation document
        return adaptationPatternNew;
    }

    // visitors for find identifiers and methods that need anonymous
    public static class ImportVisitor extends ASTVisitor {
        public List<String> libTNames=new ArrayList<>();
        public String brokenLib = "";
        public List<String> headNames=new ArrayList<>();
        @Override
        public boolean visit(ImportDeclaration node)   { // find all imported class of target broken lib
            String className = node.getName().toString();
            String[] classNameSplit = className.split("\\.");
            int length = classNameSplit.length;
            if (className.contains(brokenLib)){
                libTNames.add(classNameSplit[length-1]);
            }
            headNames.addAll(Arrays.asList(classNameSplit));
            return super.visit(node);
        }
    }
    public static class ImmediatelyVisitor extends ASTVisitor {
        public List<String> namesL = new ArrayList<>();
        @Override
        public boolean visit(StringLiteral node)   {
            String name = node.toString();
            if (!namesL.contains(name)){
                namesL.add(name);
            }
            return super.visit(node);
        }
        public boolean visit(BooleanLiteral node)   {
            String name = node.toString();
            if (!namesL.contains(name)) namesL.add(name);
            return super.visit(node);
        }
        public boolean visit(CharacterLiteral node)   {
            String name = node.toString();
            if (!namesL.contains(name))namesL.add(name);
            return super.visit(node);
        }
        public boolean visit(NumberLiteral node)   {
            String name = node.toString();
            if (!namesL.contains(name))namesL.add(name);
            return super.visit(node);
        }
        public boolean visit(TypeLiteral node)   {
            String name = node.toString();
            if (!namesL.contains(name))namesL.add(name);
            return super.visit(node);
        }
        public boolean visit(NullLiteral node)   {
            String name = node.toString();
            if (!namesL.contains(name))namesL.add(name);
            return super.visit(node);
        }
    }
    public static class MethodDeclarationVisitor extends ASTVisitor{
        public List<String> userMName=new ArrayList<>();
        @Override
        public boolean visit(MethodDeclaration node) {
            userMName.add(node.getName().toString());
            return super.visit(node);
        }
    }
    public static class VariableDeclarationVisitor extends ASTVisitor {
        public List<String> names=new ArrayList<>();
        public List<String> brokenLibClasses = new ArrayList<>();
        public List<String> userVNames = new ArrayList<>();
        public List<String> libVNames = new ArrayList<>();
        @Override
        public boolean visit(FieldDeclaration node)   { // find all concrete variables of libraries classes
            for (Object o : node.fragments()){
                if (o instanceof VariableDeclarationFragment){
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) o;
                    String vName = vdf.getName().toString();
                    names.add(vName);
                    if (brokenLibClasses.contains(node.getType().toString())){
                        libVNames.add(vName);
                    }else {
                        userVNames.add(vName);
                    }
                }
            }
            return super.visit(node);
        }
        @Override
        public boolean visit(VariableDeclarationExpression node)   { // find all concrete variables of libraries classes
            for (Object o : node.fragments()){
                if (o instanceof VariableDeclarationFragment){
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) o;
                    String vName = vdf.getName().toString();
                    names.add(vName);
                    if (brokenLibClasses.contains(node.getType().toString())){
                        libVNames.add(vName);
                    }else {
                        userVNames.add(vName);
                    }
                }
            }
            return super.visit(node);
        }
        @Override
        public boolean visit(VariableDeclarationStatement node)   { // find all concrete variables of libraries classes
            for (Object o : node.fragments()){
                if (o instanceof VariableDeclarationFragment){
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) o;
                    String vName = vdf.getName().toString();
                    names.add(vName);
                    String typeName = node.getType().toString();
                    if (brokenLibClasses.contains(typeName)){
                        libVNames.add(vName);
                    }else {
                        userVNames.add(vName);
                    }
                }
            }
            return super.visit(node);
        }
    }
    public static class SimpleTypeVisitor extends ASTVisitor {
        public List<String> tNames=new ArrayList<>();
        public List<String> libTName = new ArrayList<>();
        public List<String> userTName = new ArrayList<>();
        @Override
        public boolean visit(SimpleType node)   { // find all used type names, but not anonymous it.
            String tName = node.getName().toString();
            tNames.add(tName);
            if (!libTName.contains(tName)){
                if (!userTName.contains(tName)) userTName.add(tName);
            }
            return super.visit(node);
        }
    }
    public static class SimpleNameVisitor extends ASTVisitor {
        public List<String> vNames = new ArrayList<>();
        @Override
        public boolean visit(SimpleName node)   { // find all identifier simple names that not in front names, and anonymous it.
            String name = node.toString();
            if (!vNames.contains(name)) vNames.add(name);
            return super.visit(node);
        }
    }
    public static class MethodInvocationVisitor extends ASTVisitor {
        public List<String> mNames=new ArrayList<>();
        public List<String> userMNames = new ArrayList<>();
        public List<String> libMNames = new ArrayList<>();
        public List<String> userMQNames = new ArrayList<>();
        public List<String> libMQNames = new ArrayList<>();
        public List<String> brokenLibClasses = new ArrayList<>();
        public List<String> brokenLibObject = new ArrayList<>();
        @Override
        public boolean visit(MethodInvocation node)   {  // find all use_define method names, and anonymous it.
            String qualifiedName = "";
            String mName = node.getName().toString();
            if (node.getExpression()!=null){
                qualifiedName = node.getExpression().toString() + "."+node.getName();
            }else {
                qualifiedName = node.getName().toString();
            }
            Set<String> qualifiedNameComponent = new HashSet<>(Arrays.asList(qualifiedName.split("\\."))) ;
            Set<String> tmp1 = new HashSet<>(brokenLibClasses);// cao, 函数只有值传递，赋值有指针传递！
            Set<String> tmp2 =new HashSet<>(brokenLibObject);
            tmp1.retainAll(qualifiedNameComponent);
            tmp2.retainAll(qualifiedNameComponent);
            if (tmp1.isEmpty() && tmp2.isEmpty()){
                if (!userMQNames.contains(qualifiedName)) userMQNames.add(qualifiedName);
                userMNames.add(mName);
            }else {
                libMQNames.add(qualifiedName);
                libMNames.add(mName);
            }
            mNames.add(node.getName().toString());
            return super.visit(node);
        }
    }
    public static class FieldAccessVisitor extends ASTVisitor{//ForFindAllLibrariesFields
        public List<String> libFNames=new ArrayList<>();
        public List<String> userFName = new ArrayList<>();
        public List<String> types = new ArrayList<>();
        public List<String> variables = new ArrayList<>();
        public List<String> brokenLibClasses = new ArrayList<>();
        public List<String> brokenLibObject = new ArrayList<>();
        public List<String> importHeads = new ArrayList<>();
        @Override
        public boolean visit(QualifiedName node) {
            String qualifiedName = node.getFullyQualifiedName();
            String simpleName = node.getName().toString();
            Set<String> qualifiedNameComponent = new HashSet<>(Arrays.asList(qualifiedName.split("\\."))) ;
            Set<String> tmp1 = new HashSet<>(brokenLibClasses);// cao, 函数只有值传递，赋值有指针传递！
            Set<String> tmp2 =new HashSet<>(brokenLibObject);
            tmp1.retainAll(qualifiedNameComponent);
            tmp2.retainAll(qualifiedNameComponent);
            if (!importHeads.contains(simpleName)&&!types.contains(simpleName)&&!variables.contains(simpleName)){// 首先确保这个Qualified是filed access
                if (tmp1.isEmpty()&&tmp2.isEmpty()){
                    userFName.add(simpleName);
                }else {
                    libFNames.add(simpleName);
                }
            }
            return super.visit(node);
        }
    }
}
//import tt.Directory
//        import tt.FSDirectory
//        import tt.Analyzer
//        import tt.IndexWriterConfig
//        import tt.LUCENE_VERSION
//        import tt.IndexWriter
//        import tt.OpenMode
//
//class Test{
//    public static void main() {
//        Directory dir = FSDirectory.open(new File(indexPath));
//        Analyzer an = getAnalyzer(analyzer);
//        IndexWriterConfig iwc = new IndexWriterConfig(LUCENE_VERSION, an);
//        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
//        indexWriter = new IndexWriter(dir, iwc);
//    }
//}

//    public static class QualifiedNameVisitor extends ASTVisitor {
//        public Set<String> names=new HashSet<>();
//        public Set<String> qNames=new HashSet<>();
//        @Override
//        public boolean visit(QualifiedName node)   { // find all qualified variable names that not in front names, and anonymous it.
//            names.add(node.getName().toString());
//            qNames.add(node.getFullyQualifiedName());
//            return super.visit(node);
//        }
//    }
//    public static class FieldDeclarationVisitor extends ASTVisitor {
//        public Set<String> brokenLibClasses = new HashSet<>();
//        public Set<String> names=new HashSet<>();
//        @Override
//        public boolean visit(FieldDeclaration node)   { // find all concrete variables of libraries classes
//            String typeName = node.getType().toString();
//            if (brokenLibClasses.contains(typeName)){
//                for(Object o : node.fragments()){
//                    VariableDeclarationFragment v = (VariableDeclarationFragment) o;
//                    names.add(v.getName().toString());
//                }
//            }
//            return super.visit(node);
//        }
//    }
//    public static class VariableDeclarationStatementVisitor extends ASTVisitor {
//        public Set<String> brokenLibClasses = new HashSet<>();
//        public Set<String> names=new HashSet<>();
//        @Override
//        public boolean visit(VariableDeclarationStatement node)   { // find all concrete variables of libraries classes
//            String typeName = node.getType().toString();
//            if (brokenLibClasses.contains(typeName)){
//                for(Object o : node.fragments()){
//                    VariableDeclarationFragment v = (VariableDeclarationFragment) o;
//                    names.add(v.getName().toString());
//                }
//            }
//            return super.visit(node);
//        }
//    }
//    public static class VariableDeclarationExpressionVisitor extends ASTVisitor {
//        public Set<String> brokenLibClasses = new HashSet<>();
//        public Set<String> names=new HashSet<>();
//        @Override
//        public boolean visit(VariableDeclarationExpression node)   { // find all concrete variables of libraries classes
//            String typeName = node.getType().toString();
//            if (brokenLibClasses.contains(typeName)){
//                for(Object o : node.fragments()){
//                    VariableDeclarationFragment v = (VariableDeclarationFragment) o;
//                    names.add(v.getName().toString());
//                }
//            }
//            return super.visit(node);
//        }
//    }

//    public static Document symbolicMatchPatternAndGenerateAdaptationOld(Document brokenPattern, Document adaptationPattern, Document targetClientCode, int[] brokenPatternPositions, int[] adaptationPatternPositions, int[] targetBrokenPositions,AdaptationGenerationHelper helper){
//        // rewrite AST
//
//
//        // get map from apSymbolic2bpSymbolic
//        Map<String,String> apSymbolic2bpSymbolic = new HashMap<>();
//        Map<String, String> apSymbolic2Identifier = helper.adaptationPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//        for (String aps: apSymbolic2Identifier.keySet()){
//            String apI = apSymbolic2Identifier.get(aps);
//            if (helper.brokenPIdentifier2symbolic.keySet().contains(apI)){
//                apSymbolic2bpSymbolic.put(aps,helper.brokenPIdentifier2symbolic.get(apI));
//            }
//        }
//
//        // get bp Identifier and tb Identifier
//        SimpleNameMatch snmBrokenP = new SimpleNameMatch();
//        Map<String, String> bpSymbolic2Identifier = helper.brokenPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//        snmBrokenP.identifierNames = bpSymbolic2Identifier.keySet();
//        ASTNode bpAstNode = Util.getASTNodeFromStatementsDocument(brokenPattern); // change to block for partial statements, done
//        bpAstNode.accept(snmBrokenP);// check is visitor sequence search? yes
//
//        SimpleNameMatch snmTargetBroken = new SimpleNameMatch();
//        Map<String, String> tbSymbolic2Identifier = helper.targetBrokenIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//        snmTargetBroken.identifierNames = tbSymbolic2Identifier.keySet();
//        ASTNode tbAstNode = Util.getASTNodeFromStatementsDocument(targetClientCode);
//        tbAstNode.accept(snmTargetBroken);
//
//        // get bpSymbolic2tbSymbolic
//        int length = snmBrokenP.simpleNames.size();
//        int length2 = snmTargetBroken.simpleNames.size();
//        Map<String,String> bpSymbolic2tbSymbolic = new HashMap<>();
//        if (length2!=length){return adaptationPattern;} // review is it reasonable? yes
//        for (int i=0;i<length;i++){
//            String bpn = snmBrokenP.simpleNames.get(i);
//            String tbn = snmTargetBroken.simpleNames.get(i);
//            bpSymbolic2tbSymbolic.put(bpn,tbn);
//        }
//
//        Map<String,String> apSymbolic2tbIdentifier = new HashMap<>();
//        for (String apn : apSymbolic2bpSymbolic.keySet()){
////            String temp1 = apSymbolic2bpSymbolic.get(apn);
////            String temp2 = bpSymbolic2tbSymbolic.get(temp1);
////            String temp3 = tbSymbolic2Identifier.get(temp2);
//            String taIdentifier = tbSymbolic2Identifier.get(bpSymbolic2tbSymbolic.get(apSymbolic2bpSymbolic.get(apn)));
//            apSymbolic2tbIdentifier.put(apn,taIdentifier);
//        }
//
//        // generate target adaptation document
//        return Util.rewriteSimpleName(apSymbolic2tbIdentifier,adaptationPattern);
//    }

//        // get bp Identifier and tb Identifier
//        SimpleNameMatch snmBrokenP = new SimpleNameMatch();
//        Map<String, String> bpSymbolic2Identifier = helper.brokenPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//        snmBrokenP.identifierNames = bpSymbolic2Identifier.keySet();
//        ASTNode bpAstNode = Util.getASTNodeFromStatementsDocument(brokenPattern); // change to block for partial statements, done
//        bpAstNode.accept(snmBrokenP);// check is visitor sequence search? yes
//
//        SimpleNameMatch snmTargetBroken = new SimpleNameMatch();
//        Map<String, String> tbSymbolic2Identifier = helper.targetBrokenIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//        snmTargetBroken.identifierNames = tbSymbolic2Identifier.keySet();
//        ASTNode tbAstNode = Util.getASTNodeFromStatementsDocument(targetBroken);
//        tbAstNode.accept(snmTargetBroken);
//
//        // get bpSymbolic2tbSymbolic
//        int length = snmBrokenP.simpleNames.size();
//        int length2 = snmTargetBroken.simpleNames.size();
//        if (length2!=length){return adaptationPattern;} // review is it reasonable? yes
//        for (int i=0;i<length;i++){
//            String bpn = snmBrokenP.simpleNames.get(i);
//            String tbn = snmTargetBroken.simpleNames.get(i);
//            bpSymbolic2tbSymbolic.put(bpn,tbn);
//        }

//    public static Document symbolicMatchPatternAndGenerateAdaptation(Document brokenPattern, Document adaptationPattern, Document targetBroken,AdaptationGenerationHelper helper){
//        // rewrite AST
//
//        // consider using char list match to generate migration pair? yes 否则难以完成直接用adaptation pattern 直接生成adaptation的设定 done
//
//
//        // get map from apSymbolic2bpSymbolic
//        Map<String,String> apSymbolic2bpSymbolic = new HashMap<>();
//        Map<String, String> apSymbolic2Identifier = helper.adaptationPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//        for (String aps: apSymbolic2Identifier.keySet()){
//            String apI = apSymbolic2Identifier.get(aps);
//            if (helper.brokenPIdentifier2symbolic.keySet().contains(apI)){
//                apSymbolic2bpSymbolic.put(aps,helper.brokenPIdentifier2symbolic.get(apI));
//            }
//        }
//
//        Map<String,String> bpSymbolic2tbSymbolic = new HashMap<>();
//        String[] bpl = Util.splitWords(brokenPattern.get());
//        String[] tbl = Util.splitWords(targetBroken.get());
//        String bplLines = String.join("\n",bpl);
//        String tblLines = String.join("\n",tbl);
//
//        if (bpl.length!=tbl.length) {
//            notMatchNumber+=1;
//            throw new RuntimeException();
////            return adaptationPattern;
//        } // should flag error
//        int length3 = bpl.length;
//        for (int i=0;i<length3;i++){
//            String bpn = bpl[i];
//            String tbn = tbl[i];
//            if (bpn.startsWith("v_") || bpn.startsWith("m_")){
//                bpSymbolic2tbSymbolic.put(bpn,tbn);
//            }else if (!bpn.equals(tbn)){
//                notMatchNumber+=1;
//                throw new RuntimeException();
////                return adaptationPattern;// should flag error
//            }
//        }
//        Map<String, String> bpSymbolic2Identifier = helper.brokenPIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//        Map<String, String> tbSymbolic2Identifier = helper.targetBrokenIdentifier2symbolic.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), entry -> entry.getKey()));
//
//        Map<String,String> apSymbolic2tbIdentifier = new HashMap<>();
//        for (String apn : apSymbolic2bpSymbolic.keySet()){
////            String temp1 = apSymbolic2bpSymbolic.get(apn);
////            String temp2 = bpSymbolic2tbSymbolic.get(temp1);
////            String temp3 = tbSymbolic2Identifier.get(temp2);
//            String taIdentifier = tbSymbolic2Identifier.get(bpSymbolic2tbSymbolic.get(apSymbolic2bpSymbolic.get(apn)));
//            apSymbolic2tbIdentifier.put(apn,taIdentifier);
//        }
//
//        // generate target adaptation document
//        return Util.rewriteSimpleName(apSymbolic2tbIdentifier,adaptationPattern);
//    }

