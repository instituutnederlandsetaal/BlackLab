# Performancetests

http://localhost:8080/blacklab-server/search-test/index.html

## SORT

### ALL WORDS; BL5; PARALLEL; THUIS
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      61413

### BL5 SINGLE-THREADED ALL WORDS THUIS
Corpus URL  /blacklab-server/corpora/parlamint
patt        []
sort        hit:word:i

hits        50672559
timeMs      18295

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

hits        50672559
timeMs      1446 (1531 zonder global term ids)

### BL5 SINGLE-THREADED 'de'
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word = 'de']
sort        hit:word:i

hits        2641884
timeMs      555 (5748 zonder global term ids)

### BL4 'de'
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word = 'de']
sort        hit:word:i

hits        2641884
timeMs      638



## GROUP

### BL5 PARALLEL
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      44141

### BL5 SINGLE-THREADED
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      54044

### BL4
Corpus URL  /blacklab-server/corpora/parlamint (op PC thuis)
patt        [word != 'abcdefg']
group       hit:word:i

hits        50672559
timeMs      20994
