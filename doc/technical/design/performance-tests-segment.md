# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

NOTE:
- sorting on `docid,hitposition` is fast, while sorting on `hit:word:i` or `field:title` is slow
  so reading from the Lucene index seems to be the bottleneck. Could we cache more data in memory?

- grouping seems to always be slow; why?

## SORT

### ALL WORDS; BL5; PARALLEL
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      24229 (WERK)   <-- waarom trager dan single-threaded? ws. merge?

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

### BL5 PARALLEL
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      11042 (WERK) / 6485 (fast path met []) <-- NB dit is met 2 threads; 4 is trager!!!

### BL5 SINGLE-THREADED
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      17373 (WERK)

### BL4
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      23181 (WERK)
