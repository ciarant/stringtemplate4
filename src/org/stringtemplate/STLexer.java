package org.stringtemplate;

import org.antlr.runtime.*;

import java.util.ArrayList;
import java.util.List;

public class STLexer implements TokenSource {
    public static final char EOF = (char)-1;            // EOF char
    public static final int EOF_TYPE = CharStream.EOF;  // EOF token type

    public static class STToken extends CommonToken {
        public STToken(CharStream input, int type, int channel, int start, int stop) {
            super(input, type, channel, start, stop);
        }

        public STToken(int type, String text) { super(type, text); }

        public String toString() {
            String channelStr = "";
            if ( channel>0 ) {
                channelStr=",channel="+channel;
            }
            String txt = getText();
            if ( txt!=null ) {
                txt = txt.replaceAll("\n","\\\\n");
                txt = txt.replaceAll("\r","\\\\r");
                txt = txt.replaceAll("\t","\\\\t");
            }
            else {
                txt = "<no text>";
            }
            return "[@"+getTokenIndex()+","+start+":"+stop+"='"+txt+"',<"+STParser.tokenNames[type]+">"+channelStr+","+line+":"+getCharPositionInLine()+"]";
        }
    }

    public static final Token SKIP = new STToken(-1, "<skip>");

    // TODO: enum?
    // pasted from STParser
    public static final int RBRACK=17;
    public static final int LBRACK=16;
    public static final int ELSE=5;
    public static final int ELLIPSIS=11;
    public static final int LCURLY=20;
    public static final int BANG=10;
    public static final int EQUALS=12;
    public static final int TEXT=22;
    public static final int ID=25;
    public static final int SEMI=9;
    public static final int LPAREN=14;
    public static final int IF=4;
    public static final int ELSEIF=6;
    public static final int COLON=13;
    public static final int RPAREN=15;
    public static final int WS=27;
    public static final int COMMA=18;
    public static final int RCURLY=21;
    public static final int ENDIF=7;
    public static final int RDELIM=24;
    public static final int SUPER=8;
    public static final int DOT=19;
    public static final int LDELIM=23;
    public static final int STRING=26;
	public static final int PIPE=28;
	public static final int OR=29;
	public static final int AND=30;
	public static final int INDENT=31;
    public static final int NEWLINE=32;
    public static final int AT=33;
    public static final int REGION_END=34;

    char delimiterStartChar = '<';
    char delimiterStopChar = '>';

    boolean scanningInsideExpr = false;
	int subtemplateDepth = 0; // start out *not* in a {...} subtemplate 

    CharStream input;
    char c;        // current character
    int startCharIndex;
    int startLine;
    int startCharPositionInLine;

    List<Token> tokens = new ArrayList<Token>();

    public Token nextToken() {
        if ( tokens.size()>0 ) { return tokens.remove(0); }
        return _nextToken();
    }

    public STLexer(ANTLRStringStream input) {
		this(input, '<', '>');
    }

	public STLexer(CharStream input, char delimiterStartChar, char delimiterStopChar) {
		this.input = input;
		c = (char)input.LA(1); // prime lookahead
		this.delimiterStartChar = delimiterStartChar;
		this.delimiterStopChar = delimiterStopChar;
	}

    /** Ensure x is next character on the input stream */
    public void match(char x) {
        if ( c == x) consume();
        else throw new Error("expecting "+x+"; found "+c);
    }

    protected void consume() {
        input.consume();
        c = (char)input.LA(1);
    }

    public void emit(Token token) { tokens.add(token); }

    public Token _nextToken() {
		//System.out.println("nextToken: c="+(char)c+"@"+input.index());
        while ( true ) { // lets us avoid recursion when skipping stuff
            startCharIndex = input.index();
            startLine = input.getLine();
            startCharPositionInLine = input.getCharPositionInLine();

            if ( c==EOF ) return newToken(EOF_TYPE);
            Token t = null;
            if ( scanningInsideExpr ) t = inside();
            else t = outside();
            if ( t!=SKIP ) return t;
        }
    }

    protected Token outside() {
        if ( input.getCharPositionInLine()==0 && (c==' '||c=='\t') ) {
            while ( c==' ' || c=='\t' ) consume(); // scarf indent
            return newToken(INDENT);
        }
        if ( c==delimiterStartChar ) {
            consume();
            if ( c=='!' ) { COMMENT(); return SKIP; }
            if ( c=='\\' ) return ESCAPE(); // <\\> <\uFFFF> <\n> etc...
            scanningInsideExpr = true;
            return newToken(LDELIM);
        }
        if ( c=='\r' ) { consume(); consume(); return newToken(NEWLINE); } // \r\n -> \n
        if ( c=='\n') {	consume(); return newToken(NEWLINE); }
        if ( c=='}' && subtemplateDepth>0 ) {
            scanningInsideExpr = true;
            subtemplateDepth--;
            consume();
            return newTokenFromPreviousChar(RCURLY);
        }
        return mTEXT();
    }

    protected Token inside() {
        while ( true ) {
            switch ( c ) {
                case ' ': case '\t': case '\n': case '\r': consume(); continue;
                case '.' :
					consume();
					if ( input.LA(1)=='.' && input.LA(2)=='.' ) {
						consume();
						match('.');
						return newToken(ELLIPSIS);
					}
					return newToken(DOT);
                case ',' : consume(); return newToken(COMMA);
				case ':' : consume(); return newToken(COLON);
				case ';' : consume(); return newToken(SEMI);
                case '(' : consume(); return newToken(LPAREN);
                case ')' : consume(); return newToken(RPAREN);
                case '[' : consume(); return newToken(LBRACK);
                case ']' : consume(); return newToken(RBRACK);
				case '=' : consume(); return newToken(EQUALS);
                case '!' : consume(); return newToken(BANG);
                case '@' :
                    consume();
                    if ( c=='e' && input.LA(2)=='n' && input.LA(3)=='d' ) {
                        consume(); consume(); consume();
                        return newToken(REGION_END);
                    }
                    return newToken(AT);
                case '"' : return mSTRING();
                case '&' : consume(); match('&'); return newToken(AND); // &&
                case '|' : consume(); match('|'); return newToken(OR); // ||
				case '{' : return subTemplate();
				default:
					if ( c==delimiterStopChar ) {
						consume();
						scanningInsideExpr =false;
						return newToken(RDELIM);
					}
                    if ( isIDStartLetter(c) ) {
						Token id = mID();
						String name = id.getText();
						if ( name.equals("if") ) return newToken(IF);
						else if ( name.equals("endif") ) return newToken(ENDIF);
						else if ( name.equals("else") ) return newToken(ELSE);
						else if ( name.equals("elseif") ) return newToken(ELSEIF);
                        else if ( name.equals("super") ) return newToken(SUPER);
						return id;
					}
					RecognitionException re = new NoViableAltException("", 0, 0, input);
					if ( c==EOF ) {
						throw new STRecognitionException("EOF inside ST expression", re);						
					}
                    throw new STRecognitionException("invalid character: "+c, re);
            }
        }
    }

    Token subTemplate() {
        // look for "{ args ID (',' ID)* '|' ..."
		subtemplateDepth++;
        int m = input.mark();
        int curlyStartChar = startCharIndex;
        int curlyLine = startLine;
        int curlyPos = startCharPositionInLine;
        List<Token> argTokens = new ArrayList<Token>();
        consume();
		Token curly = newTokenFromPreviousChar(LCURLY);
        WS();
        argTokens.add( mID() );
        WS();
        while ( c==',' ) {
			consume();
            argTokens.add( newTokenFromPreviousChar(COMMA) );
            WS();
            argTokens.add( mID() );
            WS();
        }
        WS();
        if ( c=='|' ) {
			consume();
            argTokens.add( newTokenFromPreviousChar(PIPE) );
            if ( isWS(c) ) consume(); // ignore a single whitespace after |
            //System.out.println("matched args: "+argTokens);
            for (Token t : argTokens) emit(t);
			input.release(m);
			scanningInsideExpr = false;
			startCharIndex = curlyStartChar; // reset state
			startLine = curlyLine;
			startCharPositionInLine = curlyPos;
			return curly;
		}
		//System.out.println("no match rewind");
		input.rewind(m);
		startCharIndex = curlyStartChar; // reset state
		startLine = curlyLine;
        startCharPositionInLine = curlyPos;
		consume();
		scanningInsideExpr = false;
        return curly;
    }

    Token ESCAPE() {
        consume(); // kill \\
        Token t = null;
        switch ( c ) {
            case '\\' : LINEBREAK(); return SKIP;
            case 'n'  :
                t = newToken(TEXT, "\n", input.getCharPositionInLine()-2);
                break;
            case 't'  :
                t = newToken(TEXT, "\t", input.getCharPositionInLine()-2);
                break;
            case ' '  :
                t = newToken(TEXT, " ", input.getCharPositionInLine()-2);
                break;
            case 'u' : t = UNICODE(); break;
            default :
                System.err.println("bad \\ char");
        }
        consume();
        match(delimiterStopChar);
        return t;
    }

    Token UNICODE() {
        consume();
        char[] chars = new char[4];
        if ( !isUnicodeLetter(c) ) System.err.println("bad unicode char: "+c);
        chars[0] = c;
        consume();
        if ( !isUnicodeLetter(c) ) System.err.println("bad unicode char: "+c);
        chars[1] = c;
        consume();
        if ( !isUnicodeLetter(c) ) System.err.println("bad unicode char: "+c);
        chars[2] = c;
        consume();
        if ( !isUnicodeLetter(c) ) System.err.println("bad unicode char: "+c);
        chars[3] = c;
        // ESCAPE kills final char and >
        char uc = (char)Integer.parseInt(new String(chars), 16);
        return newToken(TEXT, String.valueOf(uc), input.getCharPositionInLine()-6);
    }

    Token mTEXT() {
		boolean modifiedText = false;
        StringBuilder buf = new StringBuilder();
        while ( c != EOF && c != delimiterStartChar ) {
			if ( c=='\r' || c=='\n') break;
			if ( c=='}' && subtemplateDepth>0 ) break;
            if ( c=='\\' ) {
                if ( input.LA(2)==delimiterStartChar ||
					 input.LA(2)=='}' )
				{
                    modifiedText = true;
                    consume(); // toss out \ char
                    buf.append(c); consume();
                }
                else {
                    consume();
                }
                continue;
            }
            buf.append(c);
            consume();
        }
        if ( modifiedText )	return newToken(TEXT, buf.toString());
        else return newToken(TEXT);
    }

    /** ID  :   ('a'..'z'|'A'..'Z'|'_'|'/') ('a'..'z'|'A'..'Z'|'0'..'9'|'_'|'/')* ; */
    Token mID() {
        // called from subTemplate; so keep resetting position during speculation
        startCharIndex = input.index();
        startLine = input.getLine();
        startCharPositionInLine = input.getCharPositionInLine();
        consume();
        while ( isIDLetter(c) ) {
            consume();
        }
        return newToken(ID);
    }

    /** STRING : '"' ( '\\' '"' | '\\' ~'"' | ~('\\'|'"') )* '"' ; */
    Token mSTRING() {
    	//{setText(getText().substring(1, getText().length()-1));}
        boolean sawEscape = false;
        StringBuilder buf = new StringBuilder();
        buf.append(c); consume();
        while ( c != '"' ) {
            if ( c=='\\' ) {
                sawEscape = true;
                consume();
				switch ( c ) {
					case 'n' : buf.append('\n'); break;
					case 'r' : buf.append('\r'); break;
					case 't' : buf.append('\t'); break;
                	default : buf.append(c); break;
				}
				consume();
                continue;
            }
            buf.append(c);
            consume();
        }
        buf.append(c);
        consume();
        if ( sawEscape ) return newToken(STRING, buf.toString());
        else return newToken(STRING);
    }

    void WS() {
        while ( c==' ' || c=='\t' || c=='\n' || c=='\r' ) consume();
    }

    void COMMENT() {
        match('!');
        while ( !(c=='!' && input.LA(2)==delimiterStopChar) ) consume();
        consume(); consume(); // kill !>
    }

    void LINEBREAK() {
        match('\\'); // only kill 2nd \ as outside() kills first one
        match(delimiterStopChar);
        while ( c==' ' || c=='\t' ) consume(); // scarf WS after <\\>
        if ( c=='\r' ) consume();
        match('\n');
        while ( c==' ' || c=='\t' ) consume(); // scarf any indent
        return;
    }
    
    public static boolean isIDStartLetter(char c) { return c>='a'&&c<='z' || c>='A'&&c<='Z' || c=='/'; }
	public static boolean isIDLetter(char c) { return c>='a'&&c<='z' || c>='A'&&c<='Z' || c>='0'&&c<='9' || c=='/'; }
    public static boolean isWS(char c) { return c==' ' || c=='\t' || c=='\n' || c=='\r'; }
    public static boolean isUnicodeLetter(char c) { return c>='a'&&c<='f' || c>='A'&&c<='F' || c>='0'&&c<='9'; }

    public Token newToken(int ttype) {
        STToken t = new STToken(input, ttype, Lexer.DEFAULT_TOKEN_CHANNEL,
                startCharIndex, input.index()-1);
        t.setLine(startLine);
        t.setCharPositionInLine(startCharPositionInLine);
		return t;
	}

    public Token newTokenFromPreviousChar(int ttype) {
        STToken t =
            new STToken(input, ttype, Lexer.DEFAULT_TOKEN_CHANNEL,
                input.index()-1, input.index()-1);
        t.setStartIndex(input.index()-1);
        t.setLine(input.getLine());
        t.setCharPositionInLine(input.getCharPositionInLine()-1);
        return t;
    }

    public Token newToken(int ttype, String text, int pos) {
        STToken t = new STToken(ttype, text);
        t.setLine(input.getLine());
        t.setCharPositionInLine(pos);
        return t;
    }

	public Token newToken(int ttype, String text) {
		STToken t = new STToken(ttype, text);
		t.setStartIndex(startCharIndex);
		t.setLine(startLine);
		t.setCharPositionInLine(startCharPositionInLine);
		return t;
	}

    public String getSourceName() {
        return "no idea";
    }
}
