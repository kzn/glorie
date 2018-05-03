# Generalized LR for IE (GLORIE)
GLoRIE is a rule engine for GATE framework. It is based on the GLR algorithm and focused 
on Information Extraction tasks.

GLORIE is heavily influenced by JAPE engine and complements it.

## Motivation
JAPE is an CPSL-based pattern rule engine. The heart of JAPE is the concept of patterns
over annotations. This concept generalizes the concept of regular expressions - regular
expressions are based on single characters whereas JAPE uses annotations and predicates over them
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
# Its power is limited by regular engine formalism. This is mitigated by introduction of 
  phases - a phase contains several rules that are applied independently of each other. Phases
  could be combined sequentially. 
# CPSL lacks a programmatical way to assign features of newly created annotations. JAPE mitigates
  this by allowing Java code as scripting language Right-hand side of the rule

## Grammar
```
# Grammar header block
Grammar: GrammarName // grammar name
Input: Token MorphToken // list of input annotations
output: Person Organization // list of output non-terminals, optional 
Options: option1=value1, option2=value2 ... // options, optional
Start: ?  # grammar root non-terminal
Context: Sentence // context annotation, optional
# optional
Imports: {
 // java imports classes/methods
}





```
 