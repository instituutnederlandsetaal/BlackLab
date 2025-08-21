# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

## SORT

### ALL WORDS; BL5; PARALLEL
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      26055 (WERK)

### BL5 SINGLE-THREADED ALL WORDS
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      12982 (WERK)

### BL4 ALL WORDS THUIS
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      49038

### BL5 PARALLEL 'de'
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word = 'de']
sort        hit:word:i

hits        2641884
timeMs      990 (WERK)

### BL5 SINGLE-THREADED 'de'
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word = 'de']
sort        hit:word:i

hits        2641884
timeMs      464 (5748 zonder global term ids)

### BL4 'de'
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word = 'de']
sort        hit:word:i

hits        2641884
timeMs      638



## GROUP

### BL5 PARALLEL
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      17815 (WERK)

### BL5 SINGLE-THREADED
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      42631 (WERK)

### BL4
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      20994

### BL5 PARALLEL 'de'
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        2641884
timeMs      1023 (WERK)

### BL5 SINGLE-THREADED 'de'
Corpus URL  /blacklab-server/corpora/parlamint
patt        [word != 'abcdefg']
group       hit:word:i

hits        2641884
timeMs      1822 (WERK)
