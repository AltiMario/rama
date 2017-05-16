(ns rama.main
  (:require [clojure.tools.logging  :as log]
            [safely.core            :refer [safely]]
            [mount.core             :as mount :refer [defstate]]
            [clojure.java.io        :as io]
            [clojure.edn            :as edn]
            [aleph.http             :as http]
            [compojure.api.sweet    :refer :all]
            [compojure.route :as route])
  (:gen-class))

(def path-to-conf (atom nil))                               ;we can replace value with needed path in repl and remount
(def path-to-conf-with-secret (atom nil))                   ;we can replace value with needed path in repl and remount

(defn set-conf-paths [conf-path conf-with-secret-path]
  (reset! path-to-conf conf-path)
  (reset! path-to-conf-with-secret conf-with-secret-path)
  )

(defn throw-exception [msg]
  (throw (Exception. msg))
  )

(defn readfile [file]
  (safely (-> file slurp edn/read-string)
          :on-error :log-level :trace :default nil))

(defn load-config
  ([]
   (->> ["./config/config.edn"
         (and (System/getenv "HOME") (str (System/getenv "HOME") "/config/config.edn"))
         (io/resource "config.edn")]
        (filter identity)
        (some load-config)))
  ([file]
   (when file
     (let [config (readfile file)]
       (log/debug "Attempting to read configuration from:"
                  file (if config "OK!" "FAIL!"))
       config)))
  ([file secret-file]
   (when file
     (let [config (readfile file)
           secret (readfile secret-file)]
       (log/debug "Attempting to read configuration and secret data from:"
                  file "-" secret-file (if (and config secret) "OK!" "FAIL!"))
       (merge config secret)))))

(defn- resolve-config!!
  [file secret]
  (or
    (load-config file secret)
    (load-config file)
    (load-config)
    (throw-exception "Critical error: Couldn't load the application config.")
    ))

(def app-result (promise))

(defn shutdown-app []
  (mount/stop)
  (deliver app-result 0)
  )

(defn define-urls [config handler]
  (let [{:keys [name description path]} (:api config)
        default-urls  (list
                        {:swagger
                         {:ui   (or path "/api-docs")
                          :spec "/swagger.json"
                          :data {:info {:title       (or (str name " API") "API")
                                        :description (or description "Just another API")}
                                 :tags [{:name "api", :description (or description "Just another API")}]}}
                         }
                        (GET "/healthcheck" []
                          :summary "Returns 200 ok if the service is running"
                          :return {:message String}
                          {:satus 200 :body {:message "OK"}}
                          )
                        (GET "/shutdown" []
                          (shutdown-app)
                          :summary "Shutdown app"
                          :return {:message String}
                          {:satus 200 :body {:message "OK"}}
                          )
                        (GET "/reload-conf-and-server" []
                          (let [conf-path1 @path-to-conf
                                conf-path2 @path-to-conf-with-secret]
                            (mount/stop)
                            (set-conf-paths conf-path1 conf-path2)
                            (mount/start)
                            )
                          :summary "Reload conf and server"
                          :return {:message String}
                          {:satus 200 :body {:message "OK"}}
                          )
                        (route/not-found "No such page.")
                        )
        ]
    (if (not (nil? handler))
      (into [] (conj default-urls handler))
      (into [] (identity default-urls)))
    )
  )

(defn- resolve-handler!! [handler]
  (or
    (safely
      (require (symbol (namespace handler)))
      (resolve handler)
      :on-error :default nil)
    (throw-exception (str "Critical error: Couldn't resolve the handler " handler
                          ", please ensure it is available in the classpath
                          or change config and then call (mount/stop) (mount/start)."))
    )
  )

(defn init-config []
  (when (and (nil? @path-to-conf)
             (nil? @path-to-conf-with-secret)
             (nil? (System/getenv "HOME")))
    (throw-exception "Critial error: Paths to conf files are empty.
                        Need to set paths by args to main
                        or by calling set-conf-paths function first.
                        Else application will search file %HOME%/config/config.edn.")
    )
  (resolve-config!! @path-to-conf @path-to-conf-with-secret)
  )

(defstate config
          :start (init-config))

(declare server)

(defn start-server []
  (if (:running-in-test? config)
    nil
    (let [handlerfn       (when (:handler config) (resolve-handler!! (:handler config)))
          initfn          (when (:init config)    (resolve-handler!! (:init config)))
          server-params   (:server config)]
      (when initfn (initfn config))
      (log/info "Starting server with params:" server-params)
      (-> (apply api (define-urls config handlerfn))
          (http/start-server server-params)
          )
      )
    )
  )

(defn stop-server []
  (if (:running-in-test? config)
    nil
    (.close server)
    )
  )

(defstate server
          :start  (start-server)
          :stop   (stop-server))

(defn -main [& args]
  (pr-str "Args:")
  (pr-str args)
  (case (count args)
    2   (set-conf-paths (first args) (second args))
    1   (set-conf-paths (first args)  nil)
    0   (set-conf-paths nil nil)
    )
  (mount/start)
  @app-result
  (mount/stop)
  )
