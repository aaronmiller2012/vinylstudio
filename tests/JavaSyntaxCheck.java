package com.halftonestudio;

import com.sun.source.util.JavacTask;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Parse Java syntax even when the host computer lacks Android SDK types. */
public final class JavaSyntaxCheck {
    public static void main(String[] filenames) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("Java compiler required");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager files=compiler.getStandardFileManager(diagnostics,null,null)){
            JavacTask task=(JavacTask)compiler.getTask(null,files,diagnostics,Arrays.asList("-proc:none"),null,files.getJavaFileObjectsFromStrings(Arrays.asList(filenames)));
            task.parse();
            boolean ok=true;
            for(Diagnostic<? extends JavaFileObject> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){
                System.err.println(d.getSource().getName()+":"+d.getLineNumber()+": "+d.getMessage(null));
                ok=false;
            }
            if(!ok)throw new AssertionError("Java parser reported syntax errors");
            System.out.println("PASS: Android source parses without Java syntax errors (Android typechecking requires SDK)");
        }
    }
}
