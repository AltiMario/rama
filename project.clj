(defproject rama "1.1"
  :description "Library designed to facilitate development of efficient RESTful MicroServices, by Bruno Bonacci"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.6.3"]
                 [aleph "0.4.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.brunobonacci/safely "0.2.3"]
                 [ring/ring-json "0.4.0"]
                 [metosin/compojure-api "1.1.8"]
                 [com.cognitect/transit-clj "0.8.290"]
                 [mount "0.1.11"]]

  :main rama.main

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["dev-resources"]

  :profiles {:dev {:resource-paths ["test/dev-resources"]
                   :dependencies [[org.slf4j/slf4j-log4j12 "1.7.21"]
                                  [log4j/log4j "1.2.17"
                                   :exclusions [javax.mail/mail
                                                javax.jms/jms
                                                com.sun.jdmk/jmxtools
                                                com.sun.jmx/jmxri]]
                                  [ring/ring-mock "0.3.0"]]}}
  )
