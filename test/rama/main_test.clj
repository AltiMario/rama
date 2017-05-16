(ns rama.main-test
  (:require [rama.main          :as rm]
            [aleph.http         :as http]
            [byte-streams       :as bs]
            [mount.core         :as mount :refer [defstate]])
  (:use clojure.test)
  )

(defn start-web-server []
  (rm/set-conf-paths "test/dev-resources/config-for-test.edn"
                     "test/dev-resources/config-for-test.edn")
  (mount/start)
  )

(defn start-web-server-without-secret []
  (rm/set-conf-paths "test/dev-resources/config-for-test.edn" nil)
  (mount/start)
  )

(defn start-mocked-web-server []
  (rm/set-conf-paths "test/dev-resources/config-for-test-without-server-start.edn"
                     "test/dev-resources/config-for-test-without-server-start.edn")
  (mount/start)
  )

(defn stop-web-server []
  (mount/stop)
  )

(deftest test-set-conf-paths
  (reset! rm/path-to-conf nil)
  (reset! rm/path-to-conf-with-secret nil)
  (rm/set-conf-paths "test/dev-resources/config-for-test.edn"
                     "test/dev-resources/config-for-test.edn")
  (is (= "test/dev-resources/config-for-test.edn" @rm/path-to-conf))
  (is (= "test/dev-resources/config-for-test.edn" @rm/path-to-conf-with-secret))
  )

(deftest test-start-server-without-secret
  (start-web-server)
  (is (not (nil? rm/server)))
  (let [response @(http/get "http://localhost:8080/healthcheck")]
    (is (= 200 (:status response)))
    )
  (stop-web-server)
  )

(deftest test-state-after-real-start
  (start-web-server)

  (let [config-file-data (rm/readfile "test/dev-resources/config-for-test.edn")]
    (is (= (:server config-file-data)           (:server rm/config) ))
    (is (nil? (:running-in-test? rm/config) ))
    (is (not (nil? rm/server)))
    )

  (stop-web-server)
  )

(deftest test-state-after-mocked-start
  (start-mocked-web-server)

  (let [config-file-data (rm/readfile "test/dev-resources/config-for-test-without-server-start.edn")]
    (is (= (:server config-file-data)           (:server rm/config) ))
    (is (= (:running-in-test? config-file-data) (:running-in-test? rm/config) ))
    (is (nil? rm/server))
    )

  (stop-web-server)
  )

(deftest test-healthcheck-url
  (start-web-server)
  (let [response @(http/get "http://localhost:8080/healthcheck")]
    (is (= 200 (:status response)))
    (is (= "application/json; charset=utf-8" (-> response :headers (get "Content-type"))))
    (is (= "{\"message\":\"OK\"}" (-> response :body bs/to-string)))
    )
  (stop-web-server)
  )

(deftest test-invalid-url
  (start-web-server)
  (try
    @(http/get "http://localhost:8080/invalid-url")
    (catch clojure.lang.ExceptionInfo e (let [response (.-data e)]
                         (is (= 404 (:status response)))
                         (is (= "text/html; charset=utf-8" (-> response :headers (get "Content-type"))))
                         (is (= "No such page." (-> response :body bs/to-string)))
                         ))
    )
  (stop-web-server)
  )

(deftest test-swagger-url
  (start-web-server)
  (let [response @(http/get "http://localhost:8080/api-docs")]
    (is (= 200 (:status response)))
    (is (= "text/html" (-> response :headers (get "Content-type"))))
    (is (not (nil? (-> response :body bs/to-string))))
    )
  (stop-web-server)
  )