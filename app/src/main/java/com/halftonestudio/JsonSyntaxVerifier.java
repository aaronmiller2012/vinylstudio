package com.halftonestudio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small dependency-free JSON grammar verifier for project-revision recovery.
 * Unknown fields remain untouched; this ONLY decides whether a revision can
 * safely be selected, not whether its schema is compatible with this release.
 */
final class JsonSyntaxVerifier {
    private final String text;
    private int pos;
    private JsonSyntaxVerifier(byte[] bytes){text=new String(bytes,StandardCharsets.UTF_8);}
    static void requireObject(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>ProjectArchiveReader.MAX_SETTINGS_ENTRY)
            throw new IOException("Missing or oversized project settings");
        JsonSyntaxVerifier p=new JsonSyntaxVerifier(bytes);
        p.skip();if(!p.peek('{'))throw new IOException("Project settings must be a JSON object");
        p.value(0);p.skip();if(p.pos!=p.text.length())throw new IOException("Trailing data in project settings");
    }
    private boolean peek(char c){return pos<text.length()&&text.charAt(pos)==c;}
    private void skip(){while(pos<text.length()){char c=text.charAt(pos);if(c==' '||c=='\r'||c=='\n'||c=='\t')pos++;else break;}}
    private void expect(char c)throws IOException {if(!peek(c))throw invalid();pos++;}
    private IOException invalid(){return new IOException("Invalid project settings JSON near character "+pos);}
    private void literal(String word)throws IOException {
        if(!text.startsWith(word,pos))throw invalid();pos+=word.length();
    }
    private void string()throws IOException {
        expect('"');while(pos<text.length()){
            char c=text.charAt(pos++);if(c=='"')return;
            if(c<0x20)throw invalid();
            if(c=='\\'){
                if(pos==text.length())throw invalid();
                char e=text.charAt(pos++);
                if("\"\\/bfnrt".indexOf(e)>=0)continue;
                if(e!='u'||pos+4>text.length())throw invalid();
                for(int i=0;i<4;i++)if(Character.digit(text.charAt(pos++),16)<0)throw invalid();
            }
        }throw invalid();
    }
    private void number()throws IOException {
        int begin=pos;
        if(peek('-'))pos++;
        if(peek('0'))pos++;
        else {if(pos>=text.length()||text.charAt(pos)<'1'||text.charAt(pos)>'9')throw invalid();
            while(pos<text.length()&&Character.isDigit(text.charAt(pos)))pos++;}
        if(peek('.')){pos++;int p=pos;while(pos<text.length()&&Character.isDigit(text.charAt(pos)))pos++;if(p==pos)throw invalid();}
        if(peek('e')||peek('E')){pos++;if(peek('-')||peek('+'))pos++;int p=pos;
            while(pos<text.length()&&Character.isDigit(text.charAt(pos)))pos++;if(p==pos)throw invalid();}
        if(begin==pos)throw invalid();
    }
    private void value(int depth)throws IOException {
        if(depth>64)throw new IOException("Project settings nesting limit exceeded");
        skip();if(pos>=text.length())throw invalid();char c=text.charAt(pos);
        if(c=='{'){
            pos++;skip();if(peek('}')){pos++;return;}
            for(;;){skip();string();skip();expect(':');value(depth+1);skip();
                if(peek('}')){pos++;return;}expect(',');}
        }
        if(c=='['){pos++;skip();if(peek(']')){pos++;return;}
            for(;;){value(depth+1);skip();if(peek(']')){pos++;return;}expect(',');}
        }
        if(c=='"'){string();return;}
        if(c=='t'){literal("true");return;}
        if(c=='f'){literal("false");return;}
        if(c=='n'){literal("null");return;}
        number();
    }
}
