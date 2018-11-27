grammar GLORIE;

@header {
  package name.kazennikov.glorie;
}

glr: header (production | macro | pre | post | code)+;

ident: SIMPLE;

header: name (input | output | opts |start | context | importBlock | parserContext | globalExtension | staticExtension | instanceExtension)*;
name: 'Grammar:' ident;
input: 'Input:' ident+;
output: 'Output:' ident+;
opts: 'Options:' option (',' option)*;
option: ident ('=' (ident | number))?;
start: 'Start:' ident;
importBlock: 'Import:' javaCode;
context: 'Context:' ident; // maybe change to predicate selector?

pre: 'Pre:'  (javaCode | className ';'?);
post: 'Post:' (javaCode | className ';'?) ;

parserContext: 'ParserContext:' className ';'?;
// TODO: allow code there?
globalExtension: 'GlobalExtension:' className ';'?;
staticExtension: 'StaticExtension:' className ';'?;
instanceExtension: 'InstanceExtension:' className ';'?;


className: ident (('.' | '$') ident)*; // java class name

macro: 'Macro:' ident action;
code: '@code' javaCode; // @code { code }

production: '!'? lhs lhsWeight? '->' rhs action? ('=>' postprocAction)?;
action: javaCode
	  | groovyCode
      | attrs
      | macroRef;

postprocAction: javaCode;

groovyCode: ('@groovy' | '@java') javaCode;

macroRef: '%' ident;

attrs: ('@attrs' | '@attr') javaCode;

lhs: ident;
lhsWeight: '[' Number ']';
//rhs: rhsElem+; // for strict BNF without groups and * and +
rhs: rhsOrElem ('|' rhsOrElem)*;
rhsOrElem: rhsElem+;
rhsElem: head? rhsAtom modif? label?;

rhsAtom: simpleMatcher  #simpleRHS
       | SIMPLE         #identRHS
       | Number         #numRHS
       | STRING         #stringRHS
       | TYPED_STRING   #typedStringRHS
       | QSTRING		#qstringRHS
       | TYPED_QSTRING	#typedQStringRHS
       | RESTRING		#restringRHS
       | TYPED_RESTRING	#typedREStringRHS
       | '(' rhs ')'    #groupRHS
       ;


simpleMatcher: '[' annotSpec (';' annotSpec)*']';

neg: '!' | '~';
annotSpec: neg? ident (':'  feature (',' feature)*)?;

feature: neg? featureSpec;

featureValue: head* ident;
metaFeatureValue: head* '@' ident;

simpleAccessor: featureValue
              | metaFeatureValue
              ;

accessor: simpleAccessor
        | func
        | expr
        ;



featureSpec: accessor                    #booleanFeatureSpec
           | accessor op value           #simpleFeatureSpec
           | head* op simpleMatcher      #recursiveSpec
           ;


attrName: SIMPLE
        | STRING
        ;

value: SIMPLE
     | STRING
     | number
     ;



simpleVal: value | featureValue | metaFeatureValue;

func: ident '(' (expr (',' expr)*)? ')';

expr: simpleVal
	| func
    | expr '[' expr (',' expr)* ']'
    | expr '.' ident
    ;



op: '!='        #ne
  | '=='        #eq
  | '=~'        #reFind
  | '==~'       #reMatch
  | '>'         #greater
  | '>='        #ge
  | '<'         #less
  | '<='        #le
  | '==*'       #eqci   // equal case insensitive
  | '!=*'       #neci   // not equals case insensitive
  | '~'         #bitNegative
  | '#'         #hash
  | '%'         #mod
  | '^'         #pow
  | '&'         #andOp
  | '*'         #mul
  | '/'         #div
  | SIMPLE      #custom
  ;




modif: '?'                              #optional
     | '*'                              #star
     | '+'                              #plus
     | '[' number (',' number?)? ']'    #range
     ;
label: ':' ident;

head: '^';


javaCode: '{' (SIMPLE | Number | STRING | QSTRING | RESTRING | '(' | ')' | ',' | '.' | '<' | '>' | '[' | ']' | ':' | '=' | '==' | '!=' | '=~' | '==~' | '>' | '>=' | '<=' | '+' | '-' |'!' | '|' | '\'' | '&&' | '||' | ';' | '*' | '@' | '@@' | '?' | javaCode)* '}'
        ;


attr:  attrName '=' attrValue;
attrValue: value                            #simpleValue
         |':' ident '@' ident               #asMetaFeature
         |':' ident '.' ident '.' ident     #annFeature
         |':' ident '.' ident '@' ident     #annMetaFeature
         ;



WS: (' ' | '\t' | '\n' | '\r')+ -> skip;
SINGLE_COMMENT: '//' ~('\r' | '\n')* -> skip;
COMMENT:   '/*' (.)*? '*/' -> skip;

number: Number;

Number: ('-'|'+')?
             (
               DIGITS
             | DIGITS '.' DIGITS Exponent? ('f' | 'F' | 'd' | 'D')?
             | '.' DIGITS Exponent? ('f' | 'F' | 'd' | 'D')?
             | DIGITS Exponent  ('f' | 'F' | 'd' | 'D')?
             | DIGITS Exponent? ('f' | 'F' | 'd' | 'D')
             );

fragment Exponent: ('e' | 'E') ('-'|'+')? DIGITS;

STRING : '"' (~('"' | '\\') | '\\' .)* '"';
QSTRING: '\'' (~('\'' | '\\') | '\\' .)* '\'';

RESTRING: '/' (~('/' | '\\') | '\\' .)* '/';

TYPED_STRING:JavaLetter JavaLetterOrDigit* '"' (~('"' | '\\') | '\\' .)* '"';
TYPED_QSTRING: JavaLetter JavaLetterOrDigit* '\'' (~('\'' | '\\') | '\\' .)* '\'';
TYPED_RESTRING: '/' (~('/' | '\\') | '\\' .)* '/';
fragment DIGITS: '0'..'9'+;

SIMPLE: JavaLetter JavaLetterOrDigit*
;

fragment
JavaLetter
: [a-zA-Z$_] // these are the "java letters" below 0xFF
| // covers all characters above 0xFF which are not a surrogate
~[\u0000-\u00FF\uD800-\uDBFF]
{Character.isJavaIdentifierStart(_input.LA(-1))}?
| // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
[\uD800-\uDBFF] [\uDC00-\uDFFF]
{Character.isJavaIdentifierStart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
;

fragment
JavaLetterOrDigit
: [a-zA-Z0-9$_] // these are the "java letters or digits" below 0xFF
| '-'
| // covers all characters above 0xFF which are not a surrogate
~[\u0000-\u00FF\uD800-\uDBFF]
{Character.isJavaIdentifierPart(_input.LA(-1))}?
| // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
[\uD800-\uDBFF] [\uDC00-\uDFFF]
{Character.isJavaIdentifierPart(Character.toCodePoint((char)_input.LA(-2), (char)_input.LA(-1)))}?
;
