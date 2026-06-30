# TRNG: A true random number generator
### including a Java Post-Processor :D
This took me some time, but it was fun to build! And I hope that you will enjoy this project as much as I do.


## Overview
This project is a True Random Number Generator (TRNG) using a hardware noise circuit connected to a Seed XIAO RP2040 microcontroller.
The raw random bits are then cleaned up and processed using Von Neumann correction and SHA-256 whitening.

### But what actually is a TRNG???
A TRNG is a different approach from a normal random number generator, as it's randomness comes from real, unpredictable physical phenomena. 
A PRNG (Pseudo-Random Number Generator is the equivalent of this, that we may usually use inside of a computer, as the randomness comes from pure mathematical algorithms. 
But as PRNGs are mathematical functions, they can be reversed and are thus unsafe (yes even if it takes like a gazillion years to get the result).
Therefore TRNGs are more suitable for cryptographic keys or session tokens for example, as it's impossible to reverse them.

Fun Fact! Cloudflare has their own approach to this using Lava Lamps :D
https://www.cloudflare.com/learning/ssl/lava-lamp-encryption/ 
I'd build this too, if I had some lava lamps, it's actually really cool in my option :>

## How does this work?
### The Hardware
The entropy (randomness) source is a small circuit build around a standard NPN transistor (2N3904).
Normally a transistor is used to amplify or switch electrical signals. In this circuit, the transistor is dilaberatly wired BACKWARDS, the base emitter junction is reverse-biased, meaning voltage is applied in the direction of the transistor is not designed for. At around 6-9V, this causes the junction to enter avalanche breakdown: electrons in the semiconductor material start behaving chaotically, producing a tiny but unpredictable electrical noise.

<img width="750" height="511" alt="image" src="https://github.com/user-attachments/assets/794272a4-4a31-445c-86dd-932681900ca0" /> Source from BYJU'S
and from https://en.wikipedia.org/wiki/Avalanche_breakdown 

This noise is not predictable by any mathematical formula. It comes from quantum level electron behaviour, therefore making it physically random :D

The noisy signal is then fed through a 74HC14 Schmitt-trigger inverter. The Schmitt trigger has a built in threshold: below a certain voltage it outputs a clean logic 0, above it a clean logic 1. This converts the messy noise into a digital signal that our microcontroller is able to read >:D

### The Microcontroller
The Seed XIAO RP2040 runs an Arduino sketch that repeatedly reads the GPIO pin connected to the Schmitt trigger output. Each read produces a 0 or a 1 based on the current state of the noise signal. 
The sketch samples two bits at a time with a small delay, and then passes each pair through the Von Neumann correction algorithm. (explained in the next section). Once 8 corrected bits are collected they are packed into one byte and sent over USB to PC.

### Von Neumann Correction
Now, we have a Problem :/
The noise circuit is not perfectly balanced. Depending on temperature, supply voltage, the transistor junction may produce slightly more 1s than 0s, or the other way around. A boased random source is a bad random source. I mean, no one uses weighted dice, you know (or lets hope so at least).

#### The Solution
Von Neumann correction is a mathematically proven method to completely reomve bias, regardless of how fucked up the source is.
The algorithm looks at raw bits in pairs and applies 3 rules:

| Pair  | Action | Output |
| ------------- | ------------- |  ------------- |
| 0,1  | keep  |  0  |
| 1,0  | keep  |  1  |
| 0,0  | discard  |  (nothing)  |
| 1,1  | discard  |  (nothing)  |

This really helped me to understand the whole principle, here in this example with coinflips: https://pit-claudel.fr/clement/blog/generating-uniformly-random-data-from-skewed-input-biased-coins-loaded-dice-skew-correction-and-the-von-neumann-extractor/ 

#### Trade Off
Pairs of idenctical bits are thrown away, thats why we lose a significant number of unprocessed bits. Thus making the output slower but more unbiased.

### SHA-256 Whitening
Again, there is a problem...
Von Neumann correction makes the bits unbiased, but the bits can still have subtle patterns, for example certain byte values might appear slightly more often than others, or paurs of consecutive bytes might be weakle correleated. For our usecase this would've been completly okay, but I actually wanted to do the safer method here (I did overestimate myself here).
Oh, and the whitening is added inside the testing app.
### The Solution
SHA-256 whitening takes a block of debiased bytes and runs them through the SHA-256 hash function. The output is always exactly 32 bytes (256 bits) and is statistically undistinguishable from a perfectly random uniform distribution.

### Why SHA-256?
SHA-256 is a crypthographic hash function, meaning it is designed to be completely unpredictable. A tiny change in input completely changes the output in an unpredictable way. This property makes it ideal for whitening, as any statistical structure is removed.

### What can I actually use this for?
Encryption lol. You can use the hash as password or smth or use the bytes for key in your encryption algos.

# Files
## Java-App and TRNG_APP.jar
The Java-App folder consists of the .jar and all of the project files you may need to edit in order to modify my app as you need. Maybe you'll find bugs etc.. 

Now, the interesting bit (bit haha get it?), the TRNG_APP.jar!! 
The app is able to connect to your PC's COM ports, therefore beeing able to read the outputs from your microcontroller. 
As you can see in the images, it shows all of the bits and the hash, that you get in the end after everything is processed.
IMPORTAnT: You need to have a version of JAVA installed for this. Then: double-click the .jar -> open with Java. And it should work.
But you probably have java installed, if you've ever played modded minecraft :>
<img width="624" height="285" alt="NumGenSelection" src="https://github.com/user-attachments/assets/4e00d9d7-faa7-47f8-b5b4-aea2659a22b8" />
<img width="622" height="291" alt="NumGen" src="https://github.com/user-attachments/assets/d0f4176c-0595-4073-821c-4021beccf9cc" />

## TRNG
Here, you can see the wiring diagramm for the TRNG. 
| Part  | Amount | Price |
| ------------- | ------------- |  ------------- |
| 1  | XIAO SEED RP2040  |  ~6€  |
| 1  | 2N3904  |  1  | 0.05€
| 2  | 1MΩ Resistor  |  0.07€  |
| 1  | 0.1µF Capacitor  |  0.07€  |
| 1  | 9V Battery  |  1.70€  |
| 1  | 74HC14  |  0.25€  |
| 1  | Breadboard  |  -------- |
| Some  | Wires  |  -------- |
| Total | Rounded Up |  ~10€ |
<img width="848" height="554" alt="Schematic" src="https://github.com/user-attachments/assets/a884d4c8-7e9c-48fa-ace1-63c2765a7887" />

## Test Sketch
If you don't want to build the circuit, you can still try out the app with the pre Installed RNG on the RP2040. This means, if you have a Pi Pico, or any other RP2040 based board, you can upload this sketch to it, connect it to your PC, and then start the Java App <3

# The process of making this
Well....
I've wanted to build this project over a year ago and never really started it. 
I already knew about QRNG/TRNG and how one can achieve it. 
But tbh, I really overestimated myself, as I needed a lot of help from outside to finish this, but more about this later.

First of all, I approached this project with the Plan to use a Zener Diode with the Zener effect.
https://opg.optica.org/optcon/fulltext.cfm?uri=optcon-1-7-1572 
<img width="1280" height="720" alt="image" src="https://github.com/user-attachments/assets/8b195fbe-cda2-413f-ba61-9430ff747045" />
You may see, that the avalanche and the Zener effect are both relativly close to each other. 
And it's actully easier to work with the avalanche effect, as it doesnt have signals in a μV range, that I would've needed to amplify (or at least I hope so).
There we come to the main problem, which I had over the whole project: GETTING A USABLE SIGNAL.
I've read about Op Amps etc., but I just couldnt seem to get the topic inside my head. And I've actually spent a good time of the project just reasearching them ...
Therefore I changed to the Transistor using a 9V voltage, which outputs a Voltage I can actually work with.
The app was an idea that I got later during the project, as I wanted something to test the TRNG on. Thats why I booted up the good old Java-Editor and IntelliJ to write the java app.

#### Use of AI
Claude 4.5 and Gemini helped me a lot during this project, as I needed help for basic signal processing. I've never done singal processing before. Claude also helped me to get the Test Sketch and the SHA256 part of the Java App. Lastly, it helped me with overall debugging and solving problems that I didnt want to sit on for hours on end.






