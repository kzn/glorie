#Tutorial

This tutorial assumes familiarity with the GATE architecture and ANNIE pipeline notations.
If in doubt, please consult with the GATE introduction tutorial.

We will use date parsing problem as a running example through this tutorial

# GLORIE Installation
TODO

# GLORIE concepts

* Grammar - a complete specification of information extraction process  
* Terminal - an input 
* Nonterminal a symbol constructed during parsing process
* Production action
* symbol predicate
* Interp
* production root

# GLORIE parsing algorithm
1. Build a sequence of terminals. GLORIE uses GATE annotations as terminals instead of words in traditional grammars. 
	2. If a context is specified then collect all context annotations and collect all terminals within context annotations.
2. 

# The first grammar
GLORIE is grammar-based rule engine. The rules are written as grammar productions. 
Suppose we want to extract various dates from text.

Traditionally parsing deals with accepting or recognizing a sequence of symbols.
Input symbols are called terminals and symbols that are constructed through the parsing process are called nonterminals.
GATE framework deals with annotations


The simplest grammar that extracts date looks like this:
```java
Grammar: Dates // grammar name
Input: Token Lookup Sentence // input annotation types used as terminals
Output: Date // nonterminals that are converted back to annotations. Only grammar start symbol is converted back if output is not specified 
Start: S // grammar start. GLORIE will use first production LHS if start is not specified

S -> Date // Grammar consists of Date nonterminal
Date -> Number Month  // a Date is a sequence of Number followed by a Month
Number -> [Token: kind == number] // a Number is a terminal of type 'Token' whose 'kind' feature equals to 'number'
Month -> [Lookup: majorType == monthName] // a Month is a terminal of type 'Lookup' whose 'majorType' feature equals to 'monthName'

``` 

This simple grammar will annotate strings like "19 July" as "Date". As you can see, this grammar would also annotate "139 July"
as a Date. We will fix this by changing definition of 'Number' nonterminal.
Instead of 
```java
Number -> [Token: kind == number] // a Number is a terminal of type 'Token' whose 'kind' feature equals to 'number'
``` 
we will use
```java
Number -> [Token: kind == number, @length == 1] // a Number is a terminal of type 'Token' whose 'kind' feature equals to 'number' AND terminal text length is 1 
Number -> [Token: kind == number, @length == 2] // a Number could also be a terminal of type 'Token' whose 'kind' feature equals to 'number' AND terminal text length is 2
```



# 
 
 
 # GLORIE for JAPE users
GLORIE is grammar-based rule engine. Whereas JAPE uses patterns as rules.

 
