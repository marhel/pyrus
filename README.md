# pyrus

A Clojure library that generates pairwise coverage for a set of given parameters.
The purpose is to include all possible parameter values in a minimal number of combinations,
typically much less than all possible combinations of all parameter values.

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

This will generate around 137 variations, whereas generating all possible combinations would generate _648&nbsp;000_ variations.

The output is a lazy sequence of maps. For the above parameter set, the first two results are:

    {:Router :802.11ac, :Monitor :CRT, :Storage :Floppy, :Gfx "Radeon R9 295X2", :Raid :Raid10, :Arch :ia64, :OS :MacOsX, :MBMemory 2}
    {:Router :802.11n, :Monitor :LCD, :Storage :HD, :Gfx "Radeon R9 290X", :Raid :Raid5, :Arch :x64, :OS :MacOsX, :MBMemory 4}

The number of values for each parameters are

    {:Monitor 4, :Router 5, :Storage 4, :Gfx 4, :Raid 5, :Arch 3, :OS 9, :MBMemory 15}

so we can calculate all possible combinations simply by multiplying all parameter counts together

    (* 4 5 4 4 5 3 9 15)
    => 648000

## License

Copyright © 2014, 2015 Martin Hellspong, development sponsored by factor10 Solutions AB

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
