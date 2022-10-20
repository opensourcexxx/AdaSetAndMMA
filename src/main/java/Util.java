import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.simmetrics.StringMetric;
import org.simmetrics.metrics.StringMetrics;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Util {
//    public static String[] splitWords(String content){
//        String separator1 = "[ \t\n;:.()\\[\\]{}<>,+\\-*/=|&!]";
//        String separator2 = " \t\n";
//        List<String> contentL = splitSentence(content,separator1);
//        List<String> newL = new ArrayList<>();
//        int length = contentL.size();
//        for (int i=0;i<length;i++){
//            if (contentL.get(i).equals("")) continue;
//            if (separator2.contains(contentL.get(i))) continue;
//            newL.add(contentL.get(i));
//        }
//        String[] string = new String[newL.size()];
//        newL.toArray(string);
//        return string;
//    }

    public static String[] splitWordsForExpMatch(String content){
        String separator1 = "[ \t\n;:.()\\[\\]{}<>,+\\-*/=|&!]";//"[ \t\n,;]";
        String separator2 = " \t\n";
        List<String> contentL = splitSentence(content,separator1);
        List<String> newL = new ArrayList<>();
        int length = contentL.size();
        for (int i=0;i<length;i++){
            if (contentL.get(i).equals("")) continue;
            if (separator2.contains(contentL.get(i))) continue;
            newL.add(contentL.get(i));
        }
        String[] string = new String[newL.size()];
        newL.toArray(string);
        return string;
    }

    public static List<String> splitSentence(String sentence, String separator1){
        //1. 定义匹配模式
        Pattern p = Pattern.compile(separator1);
        Matcher m = p.matcher(sentence);

        //2. 拆分句子[拆分后的句子符号也没了]
        String[] words = p.split(sentence);

        //3. 保留原来的分隔符
        List<String> wl = new ArrayList<>();
        if(words.length > 0){
            int count = 0;
            while(count < words.length){
                wl.add(words[count]);
                if(m.find()){
                    wl.add(m.group());
//                    words[count] += m.group();
                }
                count++;
            }
        }
        while (m.find()){
            wl.add(m.group());
        }
        return wl;
    }

    public static Document getMRDocument(String path) throws IOException {
        int[] position = getStartPositionAndLength(path);
        String sourcecode = getFileContentWithoutPartialPrefix(path);
        sourcecode = completeBlock(sourcecode);
        Document document = new Document(sourcecode);
        return new Document(getPatternStatements(document,position));
    }

    public static class SimpleNamePatternGenerator extends ASTVisitor{
        AST ast;
        ASTRewrite astRewrite;
        String originalIdentifierName = "";
        String symbolicIdentifierName = "";
        @Override
        public boolean visit(SimpleName node) {
            if (node.toString().equals(originalIdentifierName)){
                SimpleName sn = ast.newSimpleName(symbolicIdentifierName);
                astRewrite.replace(node,sn,null);
            }
            return super.visit(node);
        }
    }

    public static Document rewriteSimpleName(Map<String,String> map, Document document){
        for (String originalName: map.keySet()){
//            if (!originalName.startsWith("v_"))continue; // todo add support for expression

            // reload ast after every document rewrite
            ASTNode cu = getASTNodeFromStatementsDocument(document);
            AST ast = cu.getAST();
            ASTRewrite astRewrite = ASTRewrite.create(ast);

            // setting ast astRewrite visitor
            SimpleNamePatternGenerator snpg = new SimpleNamePatternGenerator();
            snpg.ast = ast;
            snpg.astRewrite = astRewrite;
//            snpg.document = document; // do not write document in a visitor!
            snpg.originalIdentifierName = originalName;
            snpg.symbolicIdentifierName = map.get(originalName);
            cu.accept(snpg);

            // rewrite
            astRewrite = snpg.astRewrite;
            TextEdit edits = astRewrite.rewriteAST(document,null); //  如果一个visitor修改多个地方，是不是会引起ast 与document不一致，好像会
            try {
                edits.apply(document);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }

        return  document;
    }

    public static Document rewriteSimpleName2(Map<String,String> map, Document document){
        String code = document.get();
        for (String originalName: map.keySet()){
            String tmp = map.get(originalName);
            if (!hasSpecialChars(originalName) || tmp.contains(originalName)){
//                code = code.replaceAll(originalName,tmp);
                code = replaceCode(code,originalName,tmp);
            }
            else {
                while (code.contains(originalName)){
//                    code = replaceCode(code,originalName,tmp);
                    code = code.replace(originalName,tmp);
                }
            }
        }
        return  new Document(code);
    }
    public static String replaceCode(String code,String originalName,String dist){
        String separator1 = "[ \t\n;:.()\\[\\]{}<>,+\\-*/=|&!]";//"[ \t\n,;]";
//        String separator2 = " \t\n";
        List<String> contentL = splitSentence(code,separator1);
        for (int i=0;i<contentL.size();i++){
            String word = contentL.get(i);
            if (word.equals(originalName)) contentL.set(i,dist);
        }
        return String.join("",contentL);
    }
    public static boolean hasSpecialChars(String name){
        String specialChars = " \t$()*+.[?\\^{|";
        boolean flag = false;
        for (char i : specialChars.toCharArray()){
            if (name.contains(String.valueOf(i))){
                flag = true;
                break;
            }
        }
        return flag;
    }

    public static int[] getStartPositionAndLength(String path) throws IOException {
        int startPosition = 0;
        int endPosition = 0;
        int length = 0;
        int [] result = new int[] {0,0,0,0};
        int linesLength = 0;

        int currentLineNumber = 0;
        int startLineNumber = 0;
        int charNumber = 0;
        boolean end = false;
        boolean findStart = false;
        boolean findN = true;
        boolean findFlag = false;
        FileInputStream fis = new FileInputStream(path);
        byte[] bys = new byte[10000];
        while (fis.read(bys, 0, bys.length)!=-1) {
            for(char c: new String(bys).toCharArray()){

                if (findFlag && (c!=' ' && c!='\t')) { // find first char of change
                    startLineNumber = currentLineNumber;
                    startPosition = charNumber-1; // why -1? remove the additional char of prefix
                    findStart = true;
                    findFlag = false;
                }

                if (findN && (c=='-'||c=='+')){ // find every prefix
                    if (linesLength == 0){findFlag = true;}
                    linesLength +=1;
                }

                if (findStart && findN && c!='-'&& c!='+'){ // find last char of change，and compute the length
                    endPosition = charNumber-1;
                    endPosition -= linesLength;
                    length = endPosition - startPosition;
                    end = true;
                    break;
                }

                if(c=='\n') {findN=true;currentLineNumber+=1;} // flag the line wrap
                else {findN=false;}

                charNumber++; // record the char number
            }
            if (end){break;}
        }
        fis.close();
        result[0] = startPosition;
        result[1] = length;
        result[2] = startLineNumber;
        result[3] = linesLength;
        return result;
    }

    public static ASTNode getASTNodeFromDocument(Document document){
        // java option
        @SuppressWarnings("unchecked")
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);

        // jdt environment
        String[] sourcepathEntries = {""};
        String[] encodings = {"UTF-8"};
        String[] classPath = {""}; // specific to jar file

        // create ASTParser and set properties
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(document.get().toCharArray());
        parser.setCompilerOptions(options);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setResolveBindings(true);
        parser.setEnvironment(classPath, sourcepathEntries, encodings, true);
        parser.setUnitName(""); // need equals to target java file
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        return cu;
    }

    public static ASTNode getASTNodeFromStatementsDocument(Document document){
        // java option
        @SuppressWarnings("unchecked")
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);

        // jdt environment
        String[] sourcepathEntries = {""};
        String[] encodings = {"UTF-8"};
        String[] classPath = {""}; // specific to jar file

        // create ASTParser and set properties
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(document.get().toCharArray());
        parser.setCompilerOptions(options);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setResolveBindings(true);
        parser.setEnvironment(classPath, sourcepathEntries, encodings, true);
        parser.setUnitName(""); // need equals to target java file
        Block block = (Block) parser.createAST(null);
        return block;
    }

    public static String getPatternStatements(Document pattern, int[] positions) {
        String sourcecode = pattern.get();
        String[] codes = sourcecode.split("\n");
        StringBuilder result = new StringBuilder();

        for (int i = 0;i<codes.length;i++){
            if (i>=positions[2] && i<positions[2]+positions[3]){
                result.append(codes[i]+"\n");
            }
        }
        return result.toString();
    }

    // utils
    public static List<String> filterStartWithUppercase(List<String> names){
        List<String> result = new ArrayList<>();
        for (String s : names){
            if (!isStartWithUppercase(s)){
                result.add(s);
            }
        }
        return result;
    }

    public static boolean isStartWithUppercase(String word){
        return Pattern.matches("^[A-Z]\\w*",word);
    }

    public static String[] getBrokenLibFromFilePath(String path){
        File f = new File(path);
        File parentFile = new File(f.getParent());
        File grandPFile = new File(parentFile.getParent());
        File grandP2File = new File(grandPFile.getParent());
        String brokenLib = "";
        String brokenClient = "";
        if (grandP2File.getName().equals("library")){
            brokenLib = grandPFile.getName().split(":")[1];
            if (brokenLib.contains("-")) brokenLib = brokenLib.split("-")[0];
            brokenClient = brokenLib;
        }else {
            File grandP3File = new File(grandP2File.getParent());
            File grandP4File = new File(grandP3File.getParent());
            brokenLib = grandP4File.getName().split("\t")[1];
            if (brokenLib.contains("-")) brokenLib = brokenLib.split("-")[0];
            brokenClient = grandP2File.getName();
            if (brokenClient.contains("-")) brokenClient = brokenClient.split("-")[0];
        }
        return new String[] {brokenLib,brokenClient};
    }

    public static String[] getBrokenAPIAndTypeFromFilePath(String path){
        File f = new File(path);
        File parentFile = new File(f.getParent());
        String brokenType = parentFile.getName().split(":")[0];
        String brokenSubType = parentFile.getName().split(":")[1];
        String brokenApi = parentFile.getName().split(":")[2];
//        String brokenType = parentFile.getName().split("_")[0]; // for windows
//        String brokenSubType = parentFile.getName().split("_")[1]; // for windows
//        String brokenApi = parentFile.getName().replace(brokenType+"_","").replace(brokenSubType+"_","");
        return new String[]{brokenType,brokenSubType,brokenApi};
    }

    public static String getFileContentWithoutPrefix(String filePath) throws IOException {
        StringBuilder content= new StringBuilder();
        String line;
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        while ((line= br.readLine())!=null){
            line = line.substring(1);
            content.append(line+"\n");
        }
        br.close();
        return content.toString();
    }

    public static String getFileContentWithoutPartialPrefix(String filePath) throws IOException {
        StringBuilder content= new StringBuilder();
        String line;
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        while ((line= br.readLine())!=null){
            if (line.startsWith("-") || line.startsWith("+") ){line = line.substring(1);}
            content.append(line+"\n");
        }
        br.close();
        return content.toString();
    }

    public static String getFileContent(String filePath) throws IOException {
        StringBuilder content= new StringBuilder();
        String line;
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        while ((line= br.readLine())!=null){
            content.append(line+"\n");
        }
        br.close();
        return content.toString();
    }

    public static void zipUncompress(String inputFile, String destDirPath) throws Exception {
        File srcFile = new File(inputFile);
        if (!srcFile.exists()) {
            throw new Exception(srcFile.getPath() + "file not exist");
        }
        ZipFile zipFile = new ZipFile(srcFile);
        Enumeration<?> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            if (entry.isDirectory()) {
//                if (entry.getName().equals("__MACOSX")) continue;
                String dirPath = destDirPath + "/" + entry.getName();
                srcFile.mkdirs();
            } else {
                File targetFile = new File(destDirPath + "/" + entry.getName());
                if (!targetFile.getParentFile().exists()) {
                    targetFile.getParentFile().mkdirs();
                }
                targetFile.createNewFile();
                InputStream is = zipFile.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(targetFile);
                int len;
                byte[] buf = new byte[1024];
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fos.close();
                is.close();
            }
        }
    }

    public static String completeBlock(String source){
        // {} todo () and ;
        int left = 0;
        int right = 0;
        for(char c: source.toCharArray()){
            if (c=='{')left+=1;
            if(c=='}')right+=1;
        }
        for(;left>right;right+=1){
            source+="}";
        }
        return source;
    }

    public static float evaluationSimilarly(String a, String b){
        String str1 = a.replaceAll("\\s*","");
        String str2 = b.replaceAll("\\s*","");
//        StringMetric metric = StringMetrics.cosineSimilarity();
        StringMetric metric1 = StringMetrics.levenshtein();
        float result = metric1.compare(str1, str2);
        return result;
    }

    public static float bleu(String a, String b){
//        BLEU.Stats stats = BLEU.compute(edge, percentage, references);

//        return BLEU.score();
        return 1;
    }

    public static List<String> removeStringForList2(List<String> list, List<String> set){
        List<String> newList = new ArrayList<>();
        for (String i : list){
            if (!set.contains(i))newList.add(i);
        }
        return newList;
    }
    public static List<String> removeStringForList(List<String> list, Set<String> set){
        List<String> newList = new ArrayList<>();
        for (String i : list){
            if (!set.contains(i))newList.add(i);
        }
        return newList;
    }

    public static Document syncGa(Document ta, Document ga) throws DiffException {
        List<String> output = IOVDetect(ta);
        Map<String,String> map = new HashMap<>();
        String[] bpl = splitWordsForExpMatch(ga.get());
        String[] tbl = splitWordsForExpMatch(ta.get());
        List<String> original = Arrays.asList(bpl);
        List<String> revised = Arrays.asList(tbl);
        Patch<String> diff = DiffUtils.diff(original, revised);
        List<AbstractDelta<String>> deltas = diff.getDeltas();
        deltas.forEach(delta -> {
            if (delta.getType() == DeltaType.CHANGE) {//修改
                Chunk<String> source = delta.getSource();
                Chunk<String> target1 = delta.getTarget();
                MeditorMerge.logger.debug("CHANGE: " + "\n- " + (source.getPosition() + 1) + " " + source.getLines() + "\n+ " + "" + (target1.getPosition() + 1) + " " + target1.getLines());
                String sS = String.join("", source.getLines());
                String tS = String.join("", target1.getLines());
                String tmpSS = sS.replaceAll("\\s*","");
                String separator1 = "[ \t\n;:.()\\[\\]{}<>,+\\-*/=|&!]";
                tmpSS = splitSentence(tmpSS,separator1).get(0);
//                if ((tmpSS.startsWith("v_")||tmpSS.startsWith("t_")||tmpSS.startsWith("m_")||tmpSS.startsWith("c_"))) {
                if (output.contains(tS) && (tmpSS.startsWith("v_")||tmpSS.startsWith("t_")||tmpSS.startsWith("m_")||tmpSS.startsWith("c_"))) {
                    map.put(sS, tS);
                }
            }
        });
        ga = rewriteSimpleName2(map,ga);
        return ga;
    }

    public static List<String> IOVDetect(Document document){
        ASTNode block = getASTNodeFromStatementsDocument(document);
        MeditorMerge.VariableDeclarationVisitor vdv = new MeditorMerge.VariableDeclarationVisitor();
        block.accept(vdv);
        List<String> output = vdv.names;
        return output;

    }

    public static SymbolicPatternAndMap getSymbolicMap(SymbolicPatternAndMap spm, List<String> identifiers, String prefix){
        Map<String,String> map = new HashMap<>();
        int length = identifiers.size();
        for (int i=0;i<length;i++){
            map.put(identifiers.get(i),prefix+i);
        }
        spm.document = rewriteSimpleName2(map,spm.document);
        spm.identifier2Symbolic.putAll(map);
        return spm;
    }

    //    public static Document syncSymbolic(Document generatedAdaptation, Document groundTruth){
//        // get bp Identifier and tb Identifier
//        SimpleNameCross sncBrokenP = new SimpleNameCross();
//        ASTNode bpAstNode = getASTNodeFromStatementsDocument(generatedAdaptation);
//        bpAstNode.accept(sncBrokenP);// check is visitor sequence search? yes
//
//        SimpleNameCross sncTargetBroken = new SimpleNameCross();
//        ASTNode tbAstNode = getASTNodeFromStatementsDocument(groundTruth);
//        tbAstNode.accept(sncTargetBroken);
//
//        // get bpSymbolic2tbSymbolic
//        int length = sncBrokenP.simpleNames.size();
//        int length2 = sncTargetBroken.simpleNames.size();
//        Map<String,String> symbolic2Symbolic = new HashMap<>();
//        if (length2!=length){return generatedAdaptation;}
//        for (int i=0;i<length;i++){
//            String bpn = sncBrokenP.simpleNames.get(i);
//            String tbn = sncTargetBroken.simpleNames.get(i);
//            symbolic2Symbolic.put(bpn,tbn);
//        }
//        return rewriteSimpleName(symbolic2Symbolic,generatedAdaptation);// 同步的时候需要一起完成，否则会读脏数据 todo rewrite together
//    }
    // visitors for generate
//    public static class SimpleNameCross extends ASTVisitor{
//        List<String> simpleNames = new ArrayList<>();
//        @Override
//        public boolean visit(SimpleName node) {
//            if (node.toString().startsWith("v_")){
//                simpleNames.add(node.toString());
//            }
//            return super.visit(node);
//        }
//    }
}
