(ns rama.main
  (:require [rama.core :as r]
            [clojure.tools.logging :as log]
            [safely.core :refer [safely]])
  (:gen-class))


(defn- resolve-handler!! [type handler]
  (or
   (safely
    (require (symbol (namespace handler)))
    (resolve handler)
    :on-error :default nil)
   (log/error "Couldn't resolve the" type "handler" handler
              ", please ensure it is available in the classpath.")
   (System/exit 1)))


(defn- resolve-config!!
  [file]
  (or
   (r/config file)
   (r/config)
   (log/error "Couldn't load the application config.")
   (System/exit 1)))


(defn start! [{:keys [server init handler] :as cfg}]
  (let [init* (when init (resolve-handler!! "init" init))
        app (resolve-handler!! "application" handler)]
    ;; Initialize configuration
    (when init*
      (init* cfg))
    ;; starting server
    (r/start-server cfg app)))


(defn -main [& args]
  (start! (resolve-config!! (first args)))
  @(promise))
