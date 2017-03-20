# rama

A Clojure library designed to facilitate development of efficient RESTful MicroServices, by Bruno Bonacci

## Usage

``` clojure
lein new sample

cd sample

#
# create config file
#
mkdir config

cat > config/config.edn <<\EOF
{:api     {:name "sample-api" :description "Just a sample usage of Rama's library" :path "/api/api-docs"}
 :server  {:port 8080}
 :handler sample.core/app
 :init    sample.core/init!}

EOF

```

* Edit your project file and add the dependency

``` clojure
(defproject sample "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main rama.main
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [rama "0.1.0-SNAPSHOT"] ;; <- add this

                 [org.slf4j/slf4j-log4j12 "1.7.21"]
                 [log4j/log4j "1.2.17"
                  :exclusions [javax.mail/mail
                               javax.jms/jms
                               com.sun.jdmk/jmxtools
                               com.sun.jmx/jmxri]]])
```

Then create your routes **src/sample/core.clj**:

``` clojure
(ns sample.core
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clojure.tools.logging :as log]))


(defn init! [config]
  (log/info "init started.")
  ;; init your db connection here
  )


;; Add your routes here:
(def app
  (context
   "/api" [] :tags ["api"]

   (GET "/plus" []
        :return {:result Long}
        :query-params [x :- Long, y :- Long]
        :summary "adds two numbers together"

        {:status 200 :body {:result (+ x y)}})

   (POST "/mul" []
         :return {:result Long}
         :body [{:keys [x y]} {:x s/Num :y s/Num}]
         :summary "multiplies two numbers together"

         {:status 200 :body {:result (* x y)}})
   ))

```

Finally just run:

``` shell
lein run
```

To start on the REPL use:

``` clojure

(require '[rama.main :refer [start!]]
         '[rama.core :as r])

(def server (start! (r/config "config/config.edn")))

```

## Troubleshooting.

Datomic/netty incompatibility problem.

https://groups.google.com/d/msg/datomic/pZombLbp-tQ/1NszpEh6BAAJ

If you use datomic please set the following exclusions and import netty version:

Fix your `project.clj` dependencies to look like:

``` clojure
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.netty/netty-all "4.1.2.Final"]
                 [com.datomic/datomic-free "0.9.5394"
                  :exclusions [org.slf4j/log4j-over-slf4j
                               org.slf4j/slf4j-nop
                               org.hornetq/hornetq-server]]
                 [rama "0.1.0-SNAPSHOT"]

                 [org.slf4j/slf4j-log4j12 "1.7.21"]
                 [log4j/log4j "1.2.17"
                  :exclusions [javax.mail/mail
                               javax.jms/jms
                               com.sun.jdmk/jmxtools
                               com.sun.jmx/jmxri]]]
```


## License

Copyright © 2016 Bruno Bonacci

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
