# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

- fetching hits is probably best done in 4 threads; more is not useful, and fetching is a only a short operation so shouldn't hog the system too much.
- sorting hits per segment and then merging is slower than collecting all hits together and using parallel quick sort on that list. We always do that now.
- grouping hits seems to be best done in 3 threads on average.

## chn-i fetch/group

showconc no && total on && maxretrieve 20000000 && verbose

threads 1 && "de" "eerste"    # 1.2s
threads 2 && "de" "eerste"    # 0.7s
threads 3 && "de" "eerste"    # 0.51s
threads 4 && "de" "eerste"    # 0.49s  <-- best
threads 5 && "de" "eerste"    # 0.68s
threads 8 && "de" "eerste"    # 0.72s

threads 1 && "de" "eerste" && group after:word:i:1    # 7.7s
threads 2 && "de" "eerste" && group after:word:i:1    # 4.2s
threads 3 && "de" "eerste" && group after:word:i:1    # 3.9s
threads 4 && "de" "eerste" && group after:word:i:1    # 3.8s
threads 5 && "de" "eerste" && group after:word:i:1    # 3.5s
threads 6 && "de" "eerste" && group after:word:i:1    # 3.6s
threads 7 && "de" "eerste" && group after:word:i:1    # 3.5s
threads 8 && "de" "eerste" && group after:word:i:1    # 3.3s

threads 1 && "de" "eerste" && group field:title    # 8.2s
threads 2 && "de" "eerste" && group field:title    # 6.5s
threads 3 && "de" "eerste" && group field:title    # 6.2s
threads 4 && "de" "eerste" && group field:title    # 6.2s
threads 5 && "de" "eerste" && group field:title    # 6.4s
threads 6 && "de" "eerste" && group field:title    # 6.7s
threads 7 && "de" "eerste" && group field:title    # 6.8s
threads 8 && "de" "eerste" && group field:title    # 6.5s

threads 1 && "wat" && group after:word:i:1    # 11.2s
threads 2 && "wat" && group after:word:i:1    # 7.6s
threads 3 && "wat" && group after:word:i:1    # 6.5s
threads 4 && "wat" && group after:word:i:1    # 6.6s
threads 5 && "wat" && group after:word:i:1    # 6.8s

threads 1 && "wat" && group field:title       # 11.5s
threads 2 && "wat" && group field:title       # 12.2s
threads 3 && "wat" && group field:title       # 12.8s
threads 4 && "wat" && group field:title       # 12.9s
threads 5 && "wat" && group field:title       # 13.1s

## SORT BY TITLE

QueryTool parlamint
showconc no && total on && maxretrieve -1 && verbose

### BL5

THUIS 25-08
threads 1 && [] && sort field:title    17.5s
threads 2 && [] && sort field:title    23s <-- merge makes it slower
threads 4 && [] && sort field:title    23s <-- I/O bound or dominated by the merge phase
threads 8 && [] && sort field:title    24s      (same)

WERK 26-08
threads 1 && [] && sort field:title    10s
threads 2 && [] && sort field:title     8.5s
threads 3 && [] && sort field:title     8.2s
threads 4 && [] && sort field:title     7.8s <-- maybe sweet spot for fetching hits? (short operation)
threads 5 && [] && sort field:title     7.8s
threads 6 && [] && sort field:title     7.8s
threads 7 && [] && sort field:title     8.2s
threads 8 && [] && sort field:title     7.6s

SVOTMC10 26-08
[] && sort field:title    9.5s

CHN-i 27-08
"water" && sort field:title   5.7s
"water" && sort hit:word:i    0.9s
"water" && group field:title  2.4s
"water" && group hit:word:i   0.6s
"de" && sort field:title      
"de" && sort hit:word:i       
"de" && group field:title     
"de" && group hit:word:i      


## SORT BY CONTEXT

QueryTool parlamint
showconc no && total on && maxretrieve -1 && verbose

### BL5

THUIS 25-08
threads 1 && [] && sort hit:word:i   17s
threads 2 && [] && sort hit:word:i   27s <-- merge makes it slower
threads 4 && [] && sort hit:word:i   32s
threads 8 && [] && sort hit:word:i   30s

WERK 26-08
threads 1 && [] && sort hit:word:i   11.5s
threads 2 && [] && sort hit:word:i   10.9s
threads 3 && [] && sort hit:word:i   10.7s
threads 4 && [] && sort hit:word:i   10.4s <-- maybe sweet spot for fetching hits? (short operation)
threads 8 && [] && sort hit:word:i   10.1s

SVOTMC10 26-08
[] && sort hit:word:i    13.5s

### BL4 ALL WORDS
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      39762 (WERK)

## GROUP

QueryTool parlamint
showconc no && total on && maxretrieve -1 && verbose

### BL5; THUIS

THUIS

threads 1 && [] && group hit:word:i     18.5s
threads 2 && [] && group hit:word:i     13.5s
threads 4 && [] && group hit:word:i     17.0s

WERK 26-08

threads 1 && [] && group hit:word:i     10.5s
threads 2 && [] && group hit:word:i      8.2s <-- best for grouping by context
threads 3 && [] && group hit:word:i      9.5s
threads 4 && [] && group hit:word:i     14.6s

SVOTMC10 26-08
[] && group hit:word:i    11.3s

### BL4

timeMs      23181 (WERK)

## GROUP BY TITLE

QueryTool parlamint
showconc no && total on && maxretrieve -1 && verbose

## BL5

THUIS

threads 1 && [] && group field:title   7s
threads 2 && [] && group field:title   6s
threads 4 && [] && group field:title   7s
threads 8 && [] && group field:title   6.5s

WERK 26-08

threads 1 && [] && group field:title    4.7s
threads 2 && [] && group field:title    2.7s
threads 3 && [] && group field:title    2.4s
threads 4 && [] && group field:title    1.7s <-- best for grouping by metadata
threads 8 && [] && group field:title    1.8s

SVOTMC10 26-08
[] && group field:title    2s



## CHN-i server perf tests BL4/BL5


BL4

(initial startup)                 # first interactive      ~ 12s
showconc no && total on && verbose
"water" && sort before:word:i:1   # word terms initialized ~218s
"de" && sort after:word:i:1       # 5M hits                ~  9s
"de" && group before:word:i:1     # 5M hits                ~ 16s


BL5

(initial startup)                 # first interactive      ~ 19s
showconc no && total on && maxretrieve 5000000 && verbose
"water" && sort before:word:i:1   # word terms initialized ~ 80s  (63% faster)
"de" && sort after:word:i:1       # 5M hits                ~  6s  (33% faster)
"de" && group before:word:i:1     # 5M hits                ~  3s  (81% faster)
