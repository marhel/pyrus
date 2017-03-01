# Pyrus

A Clojure library that generates pairwise coverage for a set of given parameters.
The purpose is to include all possible parameter values in a minimal number of combinations,
typically much less than all possible combinations of all parameter values.

## Performance and generation efficiency

Generating the smallest possible number of variations to achieve pairwise coverage has been shown to be NP-complete. Pyrus uses the PICT algorithm described in ["Pairwise Testing in the Real World: Practical Extensions to Test-Case Scenarios"](http://msdn.microsoft.com/en-us/library/cc150619.aspx) which is flexible, has reasonable performance, as well as having generation efficiency comparable to other available tools (where _efficiency_ is seen as number of variations generated). For more details on the algorithm, please read the above paper.

The PICT-tool described in the above paper is freely available from Microsoft, but its C++ source code is not. However, Microsoft has released the C# code for [TestAPI](https://testapi.codeplex.com) on CodePlex, based on the same algorithm, and Pyrus often outperforms the VariationGenerator in TestAPI v0.6, mainly due to optimizations for quickly finding the best candidate parameter interaction, which is the most common operation done.

In fact, choosing a more heuristic approach (such as picking the best interaction found after sampling five random interactions) instead of checking all possible interactions, can dramatically improve the performance of the algorithm, but sacrifices generation efficiency, i.e. it would generate slightly more variations, but considerably faster.

## Usage

As an example, generating possible computer configurations can be done by a call to ``generate``

    (ns example
      (:require [pyrus.core :as pyrus]))

    (pyrus/generate 1 {:Router [:802.11a :802.11b :802.11g :802.11n :802.11ac]
                       :Monitor [:Plasma :LED :LCD :CRT]}
                    2 {:Storage  [:SAN :SSD :HD :Floppy]
                       :Gfx      ["GeForce GTX 970" "GeForce GTX 980" "Radeon R9 290X" "Radeon R9 295X2"]
                       :Raid     [:None :Raid0 :Raid1 :Raid5 :Raid10]
                       :Arch     [:x86 :x64 :ia64]
                       :OS       [:MacOsX :Win8 :Ubuntu :Debian :Fedora :Mageia :Slackware :FreeBSD :OpenBSD]
                       :MBMemory (map #(int (Math/pow 2 %)) (range 1 16))
                       })

This will generate around 137 variations in about 0.4 seconds on my machine, whereas generating all possible combinations would generate _648&nbsp;000_ variations.

### Parameter interaction order
The numbers 1 and 2 before the parameter maps, specifies the _parameter interaction order_, or how many of the parameters needs to be covered (included/used) together. Values from first-order (1) parameters are selected _independently_ of all other parameters, and each parameter value will appear in the output at least once. For second-order (2) parameters, Pyrus will include _all possible pairs_ of parameter values in the output at least once. For third-order parameters _all parameter value triplets_ will be included at least once, and so on.

Generating a N-order coverage for N parameters, is equivalent to generating all possible combinations. Also you must have at least N parameters if generating N-wise coverage.

#### Output
The output is a lazy sequence of maps. For the above parameter set, the first eight results are:

    {:Router :802.11ac, :Monitor :CRT, :Storage :Floppy, :Gfx "Radeon R9 295X2", :Raid :Raid10, :Arch :ia64, :OS :MacOsX, :MBMemory 2}
    {:Router :802.11n, :Monitor :LCD, :Storage :HD, :Gfx "Radeon R9 290X", :Raid :Raid5, :Arch :x64, :OS :MacOsX, :MBMemory 4}
    {:Router :802.11g, :Monitor :LED, :Storage :SSD, :Gfx "GeForce GTX 980", :Raid :Raid1, :Arch :x86, :OS :MacOsX, :MBMemory 8}
    {:Router :802.11b, :Monitor :Plasma, :Storage :SAN, :Gfx "GeForce GTX 970", :Raid :Raid0, :Arch :ia64, :OS :MacOsX, :MBMemory 16}
    {:Router :802.11a, :Monitor :Plasma, :Storage :HD, :Gfx "GeForce GTX 980", :Raid :None, :Arch :ia64, :OS :MacOsX, :MBMemory 32}
    {:Router :802.11n, :Monitor :LCD, :Storage :Floppy, :Gfx "GeForce GTX 980", :Raid :Raid0, :Arch :x64, :OS :MacOsX, :MBMemory 64}
    {:Router :802.11ac, :Monitor :Plasma, :Storage :Floppy, :Gfx "Radeon R9 290X", :Raid :None, :Arch :x86, :OS :MacOsX, :MBMemory 128}
    {:Router :802.11n, :Monitor :LCD, :Storage :HD, :Gfx "Radeon R9 295X2", :Raid :Raid0, :Arch :x86, :OS :MacOsX, :MBMemory 256}

The number of values for each parameters are

    {:Monitor 4, :Router 5, :Storage 4, :Gfx 4, :Raid 5, :Arch 3, :OS 9, :MBMemory 15}

so we can calculate all possible combinations simply by multiplying all parameter counts together

    (* 4 5 4 4 5 3 9 15)
    => 648000

## Comparison to other tools
This table shows generation efficiency for tasks of different complexity. The data is mostly taken from the above mentioned Microsoft paper, but also includes Pyrus, TestAPI and QICT (from a MSDN Magazine article) and some timings for the tools available to me.

| Task           | Pyrus      | TestAPI 0.6   | PICT | QICT        | AETG | PairTest | TConfig | CTS | Jenny | DDA | AllPairs |
| ---------------|------------|---------------|------|-------------|------|----------|---------|-----|-------|-----|----------|
| 3^4            |   9        |   9 (0.0s)    |   9  |   9 (0.0s)  |   9  |   9      |   9     |   9 | 11    |   ? |   9      |
| 3^13           |  20 (0.5s) |  19 (0.3s)    |  18  |  23 (0.0s)  |  15  |  17      |  15     |  15 | 18    |  18 |  18      |
| 4^15 3^17 2^29 |  35 ( 62s) |  exception    |  37  |  46 (0.6s)  |  41  |  34      |  40     |  39 | 38    |  35 |  37      |
| 4^1 3^39 2^35  |  27 ( 87s) |  exception    |  27  |  36 (1.0s)  |  28  |  26      |  30     |  29 | 28    |  27 |  27      |
| 2^100          |  16 ( 99s) |  exception    |  15  |  16 (1.1s)  |  10  |  15      |  14     |  10 | 16    |  15 |  15      |
| 10^20          | 196 (182s) |  exception    | 210  | 221 (1.1s)  | 180  | 212      | 231     | 210 | 193   | 201 | 210      |

The task 3^13 means generating pairwise coverage for 13 parameters having 3 different values each (3^13 possible combinations).
Timings by me on my machine. I think the silly exceptions for TestAPI is because they are trying to calculate the number of all possible combinations, and overflows on some inputs. Some day I'll look into getting the source code from CodePlex to re-run TestAPI timings with this annoyance fixed.

But as a general indication of Pyrus performance contra TestAPI, Pyrus generates 77 variations for 6^20 in 27 seconds, whereas TestAPI generates 77 variations in 1 minute 47 seconds.

### QICT
From the timings alone, it seems that the simpler QICT-algoritm described in [the MSDN Magazine article "Pairwise Testing with QICT"](http://msdn.microsoft.com/en-us/magazine/ee819137.aspx) would often be a better choice than the more complex one used by PICT, TestAPI and Pyrus. Obviously that depends on if quick answers are more important to you than getting a minimal number of answers.

The QICT algorithm has a number of drawbacks, however. QICT's algorithm is weaker on average (generates more variations), but also strictly second-order, whereas Pyrus supports _mixed order_ generation as shown in the usage section. The core of the algorithmic difference between QICT and Pyrus is that QICT selects a single parameter at random, and then tries to find the best choice of value for that parameter, whereas Pyrus looks at all possible remaining parameter interaction choices (parameter-pairs, in case of pairwise generation) and tries to find the best possible pair. So for 10^20, QICT does approximately 20 "best-choice" lookups per generated variation, and Pyrus tries to find the best variation among 19&nbsp;000 (190 possible pairs, with 100 possible choices for each pair). For each choice of a pair, the number of valid pairs and variations for the remaining parameters decreases, in this example 15&nbsp;660 after choosing the first pair, 12&nbsp;640 after the second, 9&nbsp;940 7&nbsp;560 5&nbsp;500 3&nbsp;760 2&nbsp;340 1&nbsp;240 and then 460 pairs, so in total about 60&nbsp;000 "best choice" lookups for generating the first variation). Then when starting the second variation, the number of possible choices is reduced to about 17&nbsp;670.

QICT also does not support _exclusion_ (ensuring that certain combinations of values does _not_ appear in the output). TestAPI supports exclusion, but in all fairness, Pyrus has yet to implement this. But different performance vs efficiency trade-offs during generation is something I'll look into in the future.

## License

Copyright © 2014, 2015 Martin Hellspong, development sponsored by factor10 Solutions AB

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
