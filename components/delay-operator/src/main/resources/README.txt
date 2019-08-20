The Delay operator postpones sending data along a stream for a specified amount 
of time.

Example uses might include:
 - An application that needs to load a set of trading parameters prior to 
   receiving market data. 
 - Traders may want to build up a trading metric (like the beta of a portfolio) 
   by analyzing market data over a specified interval before initiating their 
   trading strategies.

The sample application includes a DelayFiveSeconds operator, to demonstrate 
delaying the stream by a constant amount of time, and a DelayVariable 
operator, to demonstrate delaying by a variable amount of time. 

Both are instances of the DelayOperator component found in the Project 
Operators drawer of the Palette view. In the Operator Properties tab of the 
Properties view, specify the delay period as a StreamBase expression that 
resolves to an interval timestamp. In the case of DelayVariable, the 
expression is a field name in the incoming stream. In the case of 
DelayFiveSeconds, the delay expression is seconds(5).

Usage Notes:
1. Data passed to the operator leaves in the same order it came out, as it 
   operates much like a First-In-First-Out queue. 
2. The data passed to the operator is not altered except for the fact that 
   the output tuple is delayed by the specified amount of time.

Version History

1.2     Fixed application pauses that may occur under rare circumstances
        when using this operator.

1.1     Fixed a deadlock bug.
