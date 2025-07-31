# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

NOTE:
- sorting on `docid,hitposition` is fast, while sorting on `hit:word:i` or `field:title` is slow
  so reading from the Lucene index seems to be the bottleneck. Could we cache more data in memory?

- grouping seems to always be slow; why?

## SORT BY TITLE

QueryTool parlamint
showconc no && maxretrieve 1000000 && verbose

## ALL WORDS; BL5; PARALLEL

threads 1 && [] && sort field:title    8.6s
threads 2 && [] && sort field:title   19.3s <-- merge makes it slow!
threads 4 && [] && sort field:title   17.7s
threads 8 && [] && sort field:title   28.5s <-- too much locking of index files?

## SORT

Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i
hits        50672559

### ALL WORDS; BL5; PARALLEL

WERK 2 THREADS: 24229   <-- waarom trager dan single-threaded? ws. merge?
THUIS 2 THREADS: 28884
THUIS 11 THREADS: 28884

### BL5 SINGLE-THREADED ALL WORDS
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      12982 (WERK)

### BL4 ALL WORDS
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      39762 (WERK)


## GROUP

Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i
hits        50672559

### BL5 PARALLEL

WERK 2 THREADS: 11042 regular path; 6485 fast path
WERK 4 THREADS: TRAGER

### BL5 SINGLE-THREADED

timeMs      17373 (WERK)

### BL4

timeMs      23181 (WERK)
