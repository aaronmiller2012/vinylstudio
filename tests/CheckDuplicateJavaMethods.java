import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/**
 * Checks for duplicate direct method declarations per class before Gradle.
 * Unlike text matching, this parses Java class members, preserves overloaded
 * signatures, and checks nested/anonymous classes independently.
 * The Android javac build remains the authoritative semantic compilation gate.
 */
public final class CheckDuplicateJavaMethods {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Supply .java files or directories");
        ArrayList<String> files = new ArrayList<>();
        for (String arg : args) {
            Path p = Paths.get(arg);
            if (Files.isDirectory(p)) {
                try (var stream=Files.walk(p)) {
                    stream.filter(f -> f.toString().endsWith(".java"))
                          .sorted().forEach(f -> files.add(f.toString()));
                }
            } else files.add(p.toString());
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if (compiler==null) throw new IllegalStateException("Java JDK compiler unavailable");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try (StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,null)) {
            var units=manager.getJavaFileObjectsFromStrings(files);
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,
                    Arrays.asList("-proc:none"),null,units);
            var trees=Trees.instance(task);
            ArrayList<String> errors=new ArrayList<>();
            int[] classes={0};
            for (CompilationUnitTree unit:task.parse()) {
                new TreePathScanner<Void,Void>() {
                    @Override public Void visitClass(ClassTree clazz,Void ignored) {
                        classes[0]++;
                        Map<String,MethodTree> seen=new HashMap<>();
                        for (Tree member:clazz.getMembers()) {
                            if (!(member instanceof MethodTree)) continue;
                            MethodTree method=(MethodTree)member;
                            StringBuilder signature=new StringBuilder(method.getName()).append('(');
                            for (VariableTree param:method.getParameters()) {
                                signature.append(param.getType().toString()).append(',');
                            }
                            signature.append(')');
                            String key=signature.toString();
                            MethodTree earlier=seen.putIfAbsent(key,method);
                            if (earlier!=null) {
                                long offset=trees.getSourcePositions().getStartPosition(unit,method);
                                long line=offset < 0 ? -1 : unit.getLineMap().getLineNumber(offset);
                                errors.add(unit.getSourceFile().getName()+":"+line+
                                    " duplicate method declaration "+key);
                            }
                        }
                        return super.visitClass(clazz,ignored);
                    }
                }.scan(unit,null);
            }
            for (Diagnostic<? extends JavaFileObject> d:diagnostics.getDiagnostics())
                if (d.getKind()==Diagnostic.Kind.ERROR) errors.add(d.toString());
            if (!errors.isEmpty()) {
                for(String error:errors) System.err.println(error);
                throw new AssertionError("FAIL duplicate Java methods / parse errors: "+errors.size());
            }
            System.out.println("PASS: duplicate method scan "+files.size()+
                               " files, "+classes[0]+" class scopes");
        }
    }
}
