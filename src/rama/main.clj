(ns rama.main
  (:require [clojure.tools.logging  :as log]
            [safely.core            :refer [safely]]
            [mount.core             :as mount :refer [defstate]]
            [clojure.java.io        :as io]
            [clojure.edn            :as edn]
            [aleph.http             :as http]
            [compojure.api.sweet    :refer :all]
            [compojure.route        :as route]
            [samsara.trackit        :refer :all]
            [rama.client            :as rc])
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
                        (GET "/healthcheck" []
                          :summary "Returns 200 ok if the service is running"
                          :return {:message String}
                          {:status 200 :body {:message "OK"}}
                          )
                        (GET "/shutdown" []
                          (shutdown-app)
                          :summary "Shutdown app"
                          :return {:message String}
                          {:status 200 :body {:message "OK"}}
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
                          {:status 200 :body {:message "OK"}}
                          )
                        (route/not-found "No such page.")
                        )
		swagger-props	{:swagger
							{:ui   (or path "/api-docs")
							:spec "/swagger.json"
							:data 
								{:info 
									{
									:title	(or (str name " API") "API")
									:description (or description "Just another API")
									}
								:tags [{:name "api", :description (or description "Just another API")}]
								}
							}
						}]
    (if (not (nil? handler))
      (into [] (-> default-urls (conj handler) (conj swagger-props)) )
      (into [] (-> default-urls (conj swagger-props))) )
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

(defn create-app
  [handler {:keys [name description path]
            :or {name "API"
                 description "Just another API"
                 path "/api-docs"}}]
  (api
   ;;
   ;; API docs
   ;;
   {:swagger
    {:ui   path
     :spec "/swagger.json"
     :data {:info {:title       name
                   :description description}
            :tags [{:name "api", :description description}]}}}
   ;;
   ;; Health check
   ;;
   (GET "/healthcheck" []
     :summary "Returns 200 ok if the service is running"
     :return {:message String}
     {:satus 200 :body {:message "OK"}})

   ;;
   ;; Application handlers
   ;;
   handler

   ;;
   ;; everything else is NOT FOUND
   ;;
   (undocumented
    (fn [_]
      {:status 404 :body {:message "Not found"}}))))

(defn run-server
  [handler {:keys [api server]}]
  (let [app (create-app handler api)]
    (http/start-server app server)))

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

(defn- check-health [config]
  (try
    (let [url-response (rc/jget (str "http://localhost:" (get-in config [:server :port]) "/healthcheck"))]
      (if (= (:message url-response) "OK")
        1
        0)
      )
    (catch Exception e
      0))
  )

(defstate health-checker
          :start (track-value-of (str "healthcheck"
                                      "."
                                      (if (some-> config :api :name)
                                        (some-> config :api :name)
                                        "rama")
                                      "."
                                      (.getHostAddress (java.net.InetAddress/getLocalHost))
                                      "."
                                      (-> config :server :port)
                                      ".value"
                                      )
                                 (fn [] (check-health config))))

(defn- start-metrics-reporter [config]
  (if-let [reporter-config (:metrics-reporter-config config)]
    {:reporter (start-reporting! {:type                         :influxdb
                                  :rate-unit                    java.util.concurrent.TimeUnit/SECONDS
                                  :duration-unit                java.util.concurrent.TimeUnit/MILLISECONDS
                                  :reporting-frequency-seconds  (:reporting-frequency-seconds reporter-config)
                                  :host                         (:host        reporter-config)
                                  :port                         (:port        reporter-config)
                                  :jvm-metrics                  :none
                                  :db-name                      (:db-name     reporter-config)
                                  :auth                         (:auth        reporter-config)
                                  :tags                         {"host"       "shryne-node"
                                                                 "version"    "0.9"}
                                  })}
    {:reporter -1}
    )
  )

(defn- stop-metrics-reporter [reporter]
  (when (not= -1 (:reporter reporter))
    ((:reporter reporter))
    )
  )

(defstate metrics-reporter
          :start  (start-metrics-reporter config)
          :stop   (stop-metrics-reporter metrics-reporter))

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
