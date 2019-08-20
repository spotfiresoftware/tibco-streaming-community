# Bollinger Bands Signaling

Bollinger Bands are a technical analysis tool that can be used to measure the 
height or depth of a price relative to previous trades. Bollinger Bands 
consist of a middle band being an N-period simple moving average (SMA), an 
upper band at +K times the standard deviation of the simple moving average 
(SMA+K*sigma), and a lower band at -K times the standard deviation of the 
simple moving average (SMA-K*sigma).

The parameters for this component can be set as constants in the Definitions 
tab of the EventFlow Editor for BollingerBandSignaling.sbapp.

The parameters are:

* **WindowSize:** The period or number of observations of the simple moving average.
* **K:** The number of standard deviations to use for the high and low Bollinger bands.
* **OrderSize:** The number of shares to trade with.

The input stream for the application accepts a security symbol, price, and 
timestamp. For each symbol provided, the application calculates the simple 
moving average and the high and low Bollinger bands. If the current price of 
the stock ever jumps above the high Bollinger band, then according to theory, 
this indicator predicts that the stock price should drop. Thus, the signaling 
algorithm takes a short position until the price of the stock passes the 
middle band from above (at which point the position is unwound). 

Alternatively, if the current price of the stock ever dips below the low 
Bollinger band, then according to theory, this indicator predicts that the 
stock price should increase. Thus, the signaling algorithm takes a long 
position until the price of the stock passes the middle band from below (at 
which point the position is unwound). 

Trades are assumed to be immediately executed with no lag or partial fills 
and the number of shares to trade are given by the OrderSize variable.

Two short feed simulations are provided. The first demonstrates how the 
component works while monitoring one stock symbol, and the second demonstrates 
how the component works while monitoring two stock symbols. The model can be 
extended to monitor as many symbols as desired, though they must all trade 
have the same parameters (K, WindowSize, OrderSize).

Values for WindowSize,  K and OrderSize can vary (and can be set manually).

The default values are:

* WindowSize = 10
* K = 1.96
* OrderSize = 1000
