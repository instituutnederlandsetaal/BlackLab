# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

## SORT

### ALL WORDS; BL5; PARALLEL
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      26055 (WERK)   <-- waarom trager dan single-threaded? merge ook parallel?

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
timeMs      17815 (WERK) / 6485 (fast path met [])

### BL5 SINGLE-THREADED
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      42631 (WERK)  <-- waarom trager dan BL4? docBase..?

### BL4
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      23181 (WERK)
