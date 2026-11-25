import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.util.*;

/** Syntax-only parser: checks Java source without Android SDK or dependency resolution. */
public final class ParseJavaSource {
    public static void main(String[] files) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> issues=new DiagnosticCollector<>();
        StandardJavaFileManager manager=compiler.getStandardFileManager(issues,null,null);
        Iterable<? extends JavaFileObject> inputs=manager.getJavaFileObjectsFromStrings(Arrays.asList(files));
        JavacTask parser=(JavacTask)compiler.getTask(null,manager,issues,Arrays.asList("-proc:none"),null,inputs);
        int total=0;for(Object ignored:parser.parse())total++;
        boolean failed=false;
        for(Diagnostic<? extends JavaFileObject> d:issues.getDiagnostics()){
            if(d.getKind()==Diagnostic.Kind.ERROR){System.err.println(d);failed=true;}
        }
        if(failed)throw new AssertionError("Java syntax errors");
        System.out.println("PASS syntax parsing for "+total+" Java source files");
        manager.close();
    }
}
