(ns chrondb.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [chrondb.core :as core]))

(deftest test-detect-mode-local
  (testing "Paths should be detected as local mode"
    (is (= :local (core/detect-mode ["./mydb"])))
    (is (= :local (core/detect-mode ["/tmp/mydb"])))
    (is (= :local (core/detect-mode ["../mydb"])))
    (is (= :local (core/detect-mode ["~/mydb"])))
    (is (= :local (core/detect-mode ["data/mydb"])))))

(deftest test-detect-mode-server
  (testing "No args should default to server mode"
    (is (= :server (core/detect-mode [])))))

(deftest test-detect-mode-help
  (testing "Help flags should be detected"
    (is (= :help (core/detect-mode ["--help"])))
    (is (= :help (core/detect-mode ["-h"])))))
