# Generalized LR for IE (GLORIE)
GLoRIE is a rule engine for GATE framework. It is based on the GLR algorithm and designed 
for Information Extraction tasks.

GLORIE is heavily influenced by JAPE and complements it.

## Motivation
JAPE is an CPSL-based pattern rule engine. The heart of JAPE is the concept of patterns
over annotations. This concept generalizes the concept of regular expressions: regular
expressions operates on single characters whereas JAPE uses annotations and predicates over them
as base elements.

For example following rule annotated any numbers followed by month name as Date:
```
Rule: MonthDate
(
	{Token.kind == number}
	{Lookup.majorType == monthName}
):span
-->
:span.Date = { rule = "MonthDate"}
```
 
JAPE is a powerful way to construct rules, however it has some important limitations:
* Its power is limited by regular expression formalism. This is partially mitigated by cascades of rules.
  Rules are grouped into phases, which contains rules that are applied independently of each other. Phases themselves
  are executed sequentially.
* CPSL lacks a programmatical way to assign features of newly created annotations. JAPE mitigates
  this by allowing Java code as scripting language Right-hand side of the rule. 
* Today Java doesn't seem a good choice as a scripting language, but there wasn't much choise when JAPE was designed.


GLoRIE is inteded to overcome some of those drawbacks. It is based on GLR - a formalism that allows ambigous context-free grammars.
A rule in GLORIE is an grammar production that describes how to rewrite a sequence of terminals.

Unlike classical grammars, GLoRIE allows attempts to construct a valid parse tree for each valid input position and allows partial parses.
For example, the relevant part of grammar that constructs a Date annotation from the above example is:
```
Number -> [Token: kind == number]
Month -> [Lookup: majorType == monthName]
Date -> Number Month

```


## GLoRIE Syntax
GLoRIE grammars constists of three major sections:
* grammar prolog, which describes basic grammar properties such as grammar name, input and output annotation types, options, etc.
* grammar preprocessing and postprocessing procedures. GLoRIE internally works with terminal and non-terminal entities. Preprocessing 
  procedure allow to write custom mapping functions that translates GATE annotations to terminals, and post-processing redefines how 
  constructed non-terminals are converted back to GATE annotations.
* the grammar itself in form of production rules

A brief description of GLORIE syntax is given below. Detailed description will follow.
```
# Grammar header block
Grammar: GrammarName // grammar name
Input: Token MorphToken // list of input annotations, they are treated as terminals in grammar terminology
output: Person Organization // list of output non-terminals, optional (it is a list of non-terminals that are converted to GATE Annotations)
Options: option1=value1, option2=value2 ... // options, optional
Start: S  # grammar root non-terminal,
Context: Sentence // context annotation, optional, whole document is used when context isn't specified

# optional
Imports: {
 // java imports classes/methods
}


```

### Grammar Header
Grammar header describes basic
### Rule Syntax
### RHS scripting
### Preprocessing and Postprocessing defaults

## GloRIE internals
### Rule Application modes
### Preprocessing and Postprocessing scripting




 