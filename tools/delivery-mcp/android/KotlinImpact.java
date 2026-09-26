import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.security.MessageDigest;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.jetbrains.kotlin.cli.jvm.compiler.*;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.lexer.KtTokens;

/** Syntax facts only: the Node graph deliberately overapproximates symbol resolution. */
public class KotlinImpact {
    static String encode(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    static String decode(String value) { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
    static void emit(String id, String kind, String pkg, Collection<String> declarations, Collection<String> references,
                     Collection<String> imports, Collection<String> tests, Collection<String> supers, Collection<String> flags) {
        System.out.println(Stream.of(id, kind, pkg, String.join("\n", declarations), String.join("\n", references),
            String.join("\n", imports), String.join("\n", tests), String.join("\n", supers), String.join("\n", flags))
            .map(KotlinImpact::encode).collect(Collectors.joining("\t")));
    }
    static boolean annotation(KtAnnotated node, String name) {
        return node.getAnnotationEntries().stream().anyMatch(a -> a.getShortName() != null && a.getShortName().asString().equals(name));
    }
    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    static String stateContract(KtFile file) {
        Set<String> names = new TreeSet<>(), operations = new TreeSet<>();
        for (KtParameter parameter : PsiTreeUtil.findChildrenOfType(file, KtParameter.class)) {
            if (parameter.getTypeReference() != null && parameter.getTypeReference().getText().contains("SavedStateHandle")) names.add(parameter.getName());
        }
        for (KtProperty property : PsiTreeUtil.findChildrenOfType(file, KtProperty.class)) {
            if (property.getTypeReference() != null && property.getTypeReference().getText().contains("SavedStateHandle")) names.add(property.getName());
        }
        if (names.isEmpty()) return null;
        for (KtNameReferenceExpression ref : PsiTreeUtil.findChildrenOfType(file, KtNameReferenceExpression.class)) {
            if (!names.contains(ref.getReferencedName())) continue;
            var owner = PsiTreeUtil.getParentOfType(ref, KtNamedFunction.class, KtProperty.class, KtClassInitializer.class);
            if (owner != null) operations.add(owner.getText().replaceAll("\\s+", ""));
        }
        for (KtStringTemplateExpression string : PsiTreeUtil.findChildrenOfType(file, KtStringTemplateExpression.class)) operations.add(string.getText());
        return digest(String.join("\n", operations));
    }
    static void kotlin(String id, String source, KtPsiFactory factory) {
        KtFile file = factory.createFile(id.substring(id.lastIndexOf('/') + 1), source);
        Set<String> declarations = new TreeSet<>(), refs = new TreeSet<>(), imports = new TreeSet<>();
        Set<String> tests = new TreeSet<>(), supers = new TreeSet<>(), flags = new TreeSet<>();
        String pkg = file.getPackageFqName().asString();
        Map<String,String> aliases = new HashMap<>();
        if (PsiTreeUtil.findChildOfType(file, PsiErrorElement.class) != null) flags.add("syntax_error");
        for (KtDeclaration declaration : file.getDeclarations()) {
            if (declaration instanceof KtNamedDeclaration named && named.getName() != null) {
                if (!named.hasModifier(KtTokens.PRIVATE_KEYWORD)) declarations.add(named.getName());
            }
            else flags.add("unknown_declaration");
        }
        for (KtNameReferenceExpression ref : PsiTreeUtil.findChildrenOfType(file, KtNameReferenceExpression.class)) refs.add(ref.getReferencedName());
        for (KtImportDirective imp : file.getImportDirectives()) {
            if (imp.getImportedFqName() == null) flags.add("unknown_import");
            else {
                imports.add(imp.getImportedFqName().asString() + (imp.isAllUnder() ? ".*" : ""));
                refs.add(imp.getImportedFqName().shortName().asString());
                if (imp.getAliasName() != null) aliases.put(imp.getAliasName(), imp.getImportedFqName().asString());
            }
        }
        for (KtClassOrObject type : PsiTreeUtil.findChildrenOfType(file, KtClassOrObject.class)) {
            if (type.getParent() instanceof KtFile) {
                boolean hasTests = type.getDeclarations().stream().anyMatch(d -> d instanceof KtNamedFunction f && annotation(f, "Test"));
                if (hasTests && type.getName() != null) tests.add(pkg + "." + type.getName());
                for (KtSuperTypeListEntry parent : type.getSuperTypeListEntries()) {
                    if (parent.getTypeReference() != null) {
                        for (KtUserType userType : PsiTreeUtil.findChildrenOfType(parent.getTypeReference(), KtUserType.class)) {
                            if (userType.getReferencedName() != null) supers.add(aliases.getOrDefault(userType.getReferencedName(), userType.getReferencedName()));
                        }
                    }
                }
            }
            if (annotation(type, "Module")) {
                boolean onlyBinds = !type.getDeclarations().isEmpty() && type.getDeclarations().stream().allMatch(d ->
                    d instanceof KtNamedFunction f && annotation(f, "Binds") && f.getBodyExpression() == null &&
                    f.getValueParameters().size() == 1 && f.getTypeReference() != null);
                flags.add(onlyBinds ? "binds_module" : "dynamic_module");
            }
        }
        for (KtNamedFunction f : PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)) {
            if (f.hasModifier(KtTokens.OPERATOR_KEYWORD) || f.hasModifier(KtTokens.INFIX_KEYWORD)) flags.add("implicit_calls");
            if (f.getParent() instanceof KtFile && annotation(f, "Test")) flags.add("unsupported_test");
        }
        if (imports.stream().anyMatch(s -> s.startsWith("java.lang.reflect") || s.startsWith("kotlin.reflect")) ||
            refs.stream().anyMatch(s -> Set.of("forName", "loadClass", "ServiceLoader", "getDeclaredMethod", "getDeclaredMethods", "getMethod", "getMethods", "MethodHandles").contains(s))) flags.add("reflection");
        if (refs.contains("getDeclaredField") || refs.contains("getDeclaredFields")) flags.add("dynamic_member");
        String state = stateContract(file);
        if (state != null) flags.add("state_contract=" + state);
        emit(id, "kotlin", pkg, declarations, refs, imports, tests, supers, flags);
    }
    static String canonical(Node node) {
        StringBuilder result = new StringBuilder(node.getNodeName());
        if (node instanceof Element element) {
            NamedNodeMap attributes = element.getAttributes();
            TreeMap<String,String> sorted = new TreeMap<>();
            for (int i=0; i<attributes.getLength(); i++) sorted.put(attributes.item(i).getNodeName(), attributes.item(i).getNodeValue());
            result.append(sorted);
        } else result.append(node.getNodeValue());
        for (Node child=node.getFirstChild(); child!=null; child=child.getNextSibling()) {
            if (child.getNodeType()!=Node.COMMENT_NODE) result.append(canonical(child));
        }
        return result.toString();
    }
    static void xml(String id, String source) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(source)));
        Element root = document.getDocumentElement();
        if (root.getTagName().equals("resources")) {
            for (Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()) {
                if (!(node instanceof Element element)) continue;
                String type=element.getTagName(), name=element.getAttribute("name");
                if (type.equals("string-array") || type.equals("integer-array")) type="array";
                Set<String> flags = new TreeSet<>();
                if (!Set.of("string","plurals","array","bool","integer","dimen","color").contains(type) || name.isBlank()) flags.add("unsupported_resource");
                emit(id + "#" + type + "/" + name, "resource", canonical(element), List.of(name, "R."+type+"."+name),
                    List.of(canonical(element)), List.of(), List.of(), List.of(), flags);
            }
        } else {
            String folder=id.substring(0,id.lastIndexOf('/')); folder=folder.substring(folder.lastIndexOf('/')+1).split("-")[0];
            String name=id.substring(id.lastIndexOf('/')+1).replaceFirst("\\.xml$", "");
            emit(id, "resource", canonical(root), List.of(name,"R."+folder+"."+name), List.of(source), List.of(), List.of(), List.of(), List.of("unsupported_resource"));
        }
    }
    public static void main(String[] args) throws Exception {
        var disposable = Disposer.newDisposable();
        try {
            var environment = KotlinCoreEnvironment.createForProduction(disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
            var factory = new KtPsiFactory(environment.getProject(), false);
            try (var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                for (String line; (line=reader.readLine())!=null;) {
                    String[] input=line.split("\t", -1); String id=decode(input[0]), source=decode(input[1]);
                    if (id.endsWith(".kt")) kotlin(id,source,factory);
                    else if (id.endsWith(".xml")) xml(id,source);
                }
            }
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
        System.exit(0); // Kotlin's application environment owns non-daemon background threads.
    }
}
