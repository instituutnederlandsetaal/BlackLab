# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

- fetching hits in multiple threads is slower than in a single thread... (3s for 2 threads vs. 2s for 1 thread, 50M hits, work pc)
- sorting is slower in multiple threads than in a single thread (17.5s for 1 thread, 23s for 2 threads, 50M hits, work pc)

## SORT BY TITLE

QueryTool parlamint
showconc no && total on && maxretrieve -1 && verbose

## BL5

THUIS
threads 1 && [] && sort field:title    17.5s
threads 2 && [] && sort field:title    23s <-- merge makes it slower
threads 4 && [] && sort field:title    23s <-- I/O bound or dominated by the merge phase
threads 8 && [] && sort field:title    24s      (same)

WERK
threads 1 && [] && sort field:title    15.5s
threads 2 && [] && sort field:title    22s <-- merge makes it slower
threads 4 && [] && sort field:title    24s <-- I/O bound or dominated by the merge phase
threads 8 && [] && sort field:title    24s      (same)

## SORT BY CONTEXT

QueryTool parlamint
showconc no && total on && maxretrieve -1 && verbose

### BL5

THUIS
threads 1 && [] && sort hit:word:i   17s
threads 2 && [] && sort hit:word:i   27s <-- merge makes it slower
threads 4 && [] && sort hit:word:i   32s
threads 8 && [] && sort hit:word:i   30s

WERK
threads 1 && [] && sort hit:word:i   15s
threads 2 && [] && sort hit:word:i   30s <-- merge makes it slower
threads 4 && [] && sort hit:word:i   33s
threads 8 && [] && sort hit:word:i   31s

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

WERK

threads 1 && [] && group hit:word:i     14.5s
threads 2 && [] && group hit:word:i     14.5s
threads 4 && [] && group hit:word:i     22s

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

WERK

threads 1 && [] && group field:title    5.0s
threads 2 && [] && group field:title    6.5s
threads 4 && [] && group field:title    9.0s
threads 8 && [] && group field:title   10.0s
