(set! *warn-on-reflection* true)

(ns minicraft.core
  (:require [minicraft.entities :as e]
            [minicraft.utils :as u]
            [play-clj.core :refer :all]
            [play-clj.g2d :refer :all]
            [play-clj.ui :refer :all]))

(defn update-screen!
  [screen entities]
  (doseq [{:keys [x y is-me?]} entities]
    (when is-me?
      (position! screen x y)))
  entities)

(defn render-or-not!
  [screen entities]
  (render! screen (remove #(= 0 (:draw-count %)) entities))
  (map (fn [{:keys [draw-count] :as e}]
         (if (and draw-count (> draw-count 0))
           (assoc e :draw-count (dec draw-count))
           e))
       entities))

(defscreen main-screen
  :on-show
  (fn [screen entities]
    (let [unit-scale (/ 1 u/pixels-per-tile)
          screen (->> (orthogonal-tiled-map "level1.tmx" unit-scale)
                      (update! screen :camera (orthographic) :renderer))
          sheet (texture "tiles.png")
          tiles (texture! sheet :split 16 16)
          player-images (for [col [0 1 2 3]]
                          (texture (aget tiles 6 col)))
          zombie-images (for [col [4 5 6 7]]
                          (texture (aget tiles 6 col)))
          slime-images (for [col [4 5]]
                         (texture (aget tiles 7 col)))
          tree-image (texture sheet :set-region 0 8 16 16)
          cactus-image (texture sheet :set-region 16 8 16 16)
          attack-down-image (texture sheet :set-region 48 0 16 8)
          attack-right-image (texture sheet :set-region 32 8 8 16)
          attack-images [attack-down-image
                         (texture attack-down-image :flip false true)
                         attack-right-image
                         attack-right-image]
          hit-image (texture sheet :set-region 40 8 16 16)]
      (->> (pvalues
             (assoc (apply e/create "grass" player-images) :is-me? true)
             (assoc (apply e/create nil attack-images)
                    :attack? true :draw-count 0)
             (assoc (e/create nil hit-image)
                    :hit? true :draw-count 0)
             (take 5 (repeatedly #(apply e/create "grass" zombie-images)))
             (take 5 (repeatedly #(apply e/create "grass" slime-images)))
             (take 20 (repeatedly #(e/create "grass" tree-image)))
             (take 10 (repeatedly #(e/create "desert" cactus-image))))
           flatten
           (reduce
             (fn [entities entity]
               (conj entities (e/randomize-location screen entities entity)))
             []))))
  :on-render
  (fn [screen entities]
    (clear!)
    (->> entities
         (pmap (fn [entity]
                 (->> entity
                      (e/move screen)
                      (e/animate screen)
                      (e/animate-attack screen entities)
                      (e/animate-hit entities)
                      (e/prevent-move (remove #(= % entity) entities)))))
         e/order-by-latitude
         (render-or-not! screen)
         (update-screen! screen)))
  :on-resize
  (fn [screen entities]
    (height! screen u/vertical-tiles)
    nil)
  :on-key-down
  (fn [{:keys [keycode]} entities]
    (when-let [me (->> entities (filter :is-me?) first)]
      (cond
        (= keycode (key-code :space))
        (e/attack entities me))))
  :on-touch-down
  (fn [{:keys [x y]} entities]
    (let [entity (->> entities (filter :is-me?) first)
          min-x (/ (game :width) 3)
          max-x (* (game :width) (/ 2 3))
          min-y (/ (game :height) 3)
          max-y (* (game :height) (/ 2 3))]
      (cond
        (and (< min-x x max-x) (< min-y y max-y))
        (e/attack entity entities)))))

(defscreen text-screen
  :on-show
  (fn [screen entities]
    (update! screen :camera (orthographic) :renderer (stage))
    (assoc (label "0" (color :white))
           :id :fps
           :x 5))
  :on-render
  (fn [screen entities]
    (->> (for [entity entities]
           (case (:id entity)
             :fps (doto entity (label! :set-text (str (game :fps))))
             entity))
         (render! screen)))
  :on-resize
  (fn [screen entities]
    (height! screen 300)
    nil))

(defgame minicraft
  :on-create
  (fn [this]
    (set-screen! this main-screen text-screen)))
