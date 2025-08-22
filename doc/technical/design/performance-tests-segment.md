# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

NOTE:
- sorting on `docid,hitposition` is fast, while sorting on `hit:word:i` or `field:title` is slow
  so reading from the Lucene index seems to be the bottleneck. Could we cache more data in memory?

- grouping seems to always be slow; why?

## SORT

Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i
hits        50672559

### ALL WORDS; BL5; PARALLEL

WERK 2 THREADS: 24229   <-- waarom trager dan single-threaded? ws. merge?
THUIS 6 THREADS: 34959

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
THUIS 6 THREADS: ~14s

### BL5 SINGLE-THREADED

timeMs      17373 (WERK)

### BL4

timeMs      23181 (WERK)
