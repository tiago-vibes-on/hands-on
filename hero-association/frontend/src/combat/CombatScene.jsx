import Phaser from 'phaser'
import { useEffect, useRef } from 'react'

const COMBAT_HEIGHT = 330

function snapshotCombatant(combatant) {
  return {
    id: combatant.id,
    name: combatant.name,
    role: combatant.role,
    maxHealth: combatant.maxHealth,
    currentHealth: combatant.currentHealth,
    maxMana: combatant.maxMana,
    currentMana: combatant.currentMana,
    level: combatant.level,
    damage: combatant.damage,
    attackInterval: combatant.attackInterval,
    color: combatant.color,
    alive: combatant.alive,
  }
}

function CombatScene({ battle, onBattleChange, onCombatEvent }) {
  const hostRef = useRef(null)
  const callbacksRef = useRef({ onBattleChange, onCombatEvent })
  const initialBattleRef = useRef(battle)

  callbacksRef.current = { onBattleChange, onCombatEvent }
  if (initialBattleRef.current.encounterId !== battle.encounterId) {
    initialBattleRef.current = battle
  }

  useEffect(() => {
    const host = hostRef.current
    const initialBattle = initialBattleRef.current
    if (!host) {
      return undefined
    }

    class BattleScene extends Phaser.Scene {
      constructor() {
        super('battle')
        this.heroes = initialBattle.heroes.map((hero) => ({ ...hero }))
        this.creatures = initialBattle.creatures.map((creature) => ({ ...creature }))
        this.status = initialBattle.status
        this.resizeHandler = this.layout.bind(this)
      }

      create() {
        this.field = this.add.graphics()
        this.add.text(22, 20, 'DAWNWATCH PARTY', {
          color: '#dce4f5',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '10px',
          fontStyle: 'bold',
          letterSpacing: 1,
        })
        this.enemyLabel = this.add.text(0, 20, 'CREATURES', {
          color: '#dce4f5',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '10px',
          fontStyle: 'bold',
          letterSpacing: 1,
        }).setOrigin(1, 0)

        this.heroes.forEach((hero, index) => this.createCombatant(hero, index, 'heroes'))
        this.creatures.forEach((creature, index) => this.createCombatant(creature, index, 'creatures'))
        this.scale.on('resize', this.resizeHandler)
        this.layout()

        if (this.status === 'in-progress') {
          this.emitEvent('Combat started. Every combatant attacks on its own timer.')
          this.heroes.forEach((hero, index) => this.scheduleAttack(hero, 480 + (index * 170)))
          this.creatures.forEach((creature, index) => this.scheduleAttack(creature, 760 + (index * 160)))
        }
      }

      createCombatant(combatant, index, side) {
        const resourceTextX = side === 'heroes' ? -39 : 47
        const resourceTextOrigin = side === 'heroes' ? 1 : 0
        const resourceValueX = side === 'heroes' ? -34 : 42
        const resourceValueOrigin = side === 'heroes' ? 0 : 1
        const container = this.add.container(0, 0)
        const body = this.add.circle(0, 0, 26, combatant.color, 1)
        const ring = this.add.circle(0, 0, 26).setStrokeStyle(2, 0xf3e9ca, 0.55)
        const initial = this.add.text(0, 0, combatant.name.slice(0, 1), {
          color: '#fff8e7',
          fontFamily: 'Georgia, serif',
          fontSize: '20px',
          fontStyle: 'bold',
        }).setOrigin(0.5)
        const name = this.add.text(0, 44, combatant.name, {
          color: '#e6e9f0',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '11px',
          fontStyle: 'bold',
        }).setOrigin(0.5)
        const role = this.add.text(0, 59, combatant.role ?? 'Creature', {
          color: '#94a0b9',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '9px',
        }).setOrigin(0.5)
        const healthTrack = this.add.rectangle(4, 74, 78, 8, 0x090e19, 1).setStrokeStyle(1, 0x56607a, 0.6)
        const healthValue = this.add.rectangle(resourceValueX, 74, 76, 6, 0x70bf8b, 1).setOrigin(resourceValueOrigin, 0.5)
        const healthText = this.add.text(resourceTextX, 74, '', {
          color: '#b9c4d8',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '9px',
          fontStyle: 'bold',
        }).setOrigin(resourceTextOrigin, 0.5)
        const elements = [body, ring, initial, name, role, healthTrack, healthValue, healthText]

        if (combatant.level) {
          const levelBadge = this.add.circle(-23, -23, 11, 0x283b61, 1).setStrokeStyle(1, 0xd9b560, 0.9)
          const levelText = this.add.text(-23, -23, combatant.level, {
            color: '#f7e5ae',
            fontFamily: 'system-ui, sans-serif',
            fontSize: '10px',
            fontStyle: 'bold',
          }).setOrigin(0.5)
          elements.push(levelBadge, levelText)
        }

        if (combatant.maxMana) {
          const manaTrack = this.add.rectangle(4, 88, 78, 8, 0x090e19, 1).setStrokeStyle(1, 0x56607a, 0.6)
          const manaValue = this.add.rectangle(resourceValueX, 88, 76, 6, 0x5d92df, 1).setOrigin(resourceValueOrigin, 0.5)
          const manaText = this.add.text(resourceTextX, 88, '', {
            color: '#b9c4d8',
            fontFamily: 'system-ui, sans-serif',
            fontSize: '9px',
            fontStyle: 'bold',
          }).setOrigin(resourceTextOrigin, 0.5)
          combatant.view = { manaText, manaValue }
          elements.push(manaTrack, manaValue, manaText)
        }

        const firstSpellSlot = this.add.rectangle(-11, 104, 18, 18, 0x111a2b, 1).setStrokeStyle(1, 0x67738f, 0.9)
        const secondSpellSlot = this.add.rectangle(11, 104, 18, 18, 0x111a2b, 1).setStrokeStyle(1, 0x67738f, 0.9)
        elements.push(firstSpellSlot, secondSpellSlot)

        container.add(elements)
        combatant.view = { ...combatant.view, container, healthValue, healthText, side, index }
        this.refreshCombatant(combatant)
      }

      layout() {
        const { width, height } = this.scale
        this.field.clear()
        this.field.fillStyle(0x121b2e, 1)
        this.field.fillRect(0, 0, width, height)
        this.field.fillStyle(0x16233a, 1)
        this.field.fillRect(14, 53, width - 28, height - 68)
        this.field.lineStyle(1, 0x33425e, 0.7)
        this.field.strokeRect(14, 53, width - 28, height - 68)
        this.field.lineBetween(width / 2, 67, width / 2, height - 28)
        this.enemyLabel.setX(width - 22)

        this.positionSide(this.heroes, 'heroes', width, height)
        this.positionSide(this.creatures, 'creatures', width, height)
      }

      positionSide(combatants, side, width, height) {
        const padding = Math.min(78, Math.max(48, width * 0.1))
        const left = side === 'heroes' ? padding : width * 0.58
        const right = side === 'heroes' ? width * 0.42 : width - padding
        const spacing = combatants.length === 1 ? 0 : (right - left) / (combatants.length - 1)

        combatants.forEach((combatant, index) => {
          const formationIndex = side === 'heroes' ? combatants.length - 1 - index : index
          combatant.view.homeX = combatants.length === 1 ? (left + right) / 2 : left + (formationIndex * spacing)
          combatant.view.homeY = height * 0.5
          combatant.view.container.setPosition(combatant.view.homeX, combatant.view.homeY)
        })
      }

      scheduleAttack(attacker, delay) {
        this.time.delayedCall(delay, () => {
          if (this.status !== 'in-progress' || !attacker.alive) {
            return
          }

          const targets = attacker.view.side === 'heroes' ? this.creatures : this.heroes
          const target = targets.find((combatant) => combatant.alive)
          if (!target) {
            return
          }

          this.performAttack(attacker, target)
          if (this.status === 'in-progress') {
            this.scheduleAttack(attacker, attacker.attackInterval)
          }
        })
      }

      performAttack(attacker, target) {
        target.currentHealth = Math.max(0, target.currentHealth - attacker.damage)
        const direction = attacker.view.side === 'heroes' ? 1 : -1

        this.tweens.add({
          targets: attacker.view.container,
          x: attacker.view.homeX + (18 * direction),
          yoyo: true,
          duration: 120,
        })
        this.tweens.add({
          targets: target.view.container,
          alpha: 0.35,
          yoyo: true,
          duration: 110,
        })
        this.showDamage(target, attacker.damage)
        this.refreshCombatant(target)
        this.emitEvent(`${attacker.name} hits ${target.name} for ${attacker.damage}.`)

        if (target.currentHealth === 0) {
          target.alive = false
          target.view.container.setAlpha(0.32)
          this.emitEvent(`${target.name} is defeated.`)
          this.finishIfNeeded()
        }

        this.publishState()
      }

      showDamage(target, damage) {
        const text = this.add.text(target.view.homeX, target.view.homeY - 43, `-${damage}`, {
          color: '#f4ce78',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '15px',
          fontStyle: 'bold',
        }).setOrigin(0.5)

        this.tweens.add({
          targets: text,
          y: text.y - 20,
          alpha: 0,
          duration: 650,
          onComplete: () => text.destroy(),
        })
      }

      refreshCombatant(combatant) {
        const percentage = combatant.currentHealth / combatant.maxHealth
        combatant.view.healthValue.displayWidth = 76 * percentage
        combatant.view.healthValue.setFillStyle(percentage > 0.35 ? 0x70bf8b : 0xd99462)
        combatant.view.healthText.setText(combatant.currentHealth)
        if (combatant.view.manaValue) {
          const manaPercentage = combatant.currentMana / combatant.maxMana
          combatant.view.manaValue.displayWidth = 76 * manaPercentage
          combatant.view.manaText.setText(combatant.currentMana)
        }
      }

      finishIfNeeded() {
        const heroesAlive = this.heroes.some((hero) => hero.alive)
        const creaturesAlive = this.creatures.some((creature) => creature.alive)
        if (heroesAlive && creaturesAlive) {
          return
        }

        this.status = heroesAlive ? 'victory' : 'defeat'
        this.emitEvent(heroesAlive ? 'Encounter cleared. The party can continue the quest.' : 'The party has been defeated.')
      }

      emitEvent(message) {
        callbacksRef.current.onCombatEvent(message)
      }

      publishState() {
        callbacksRef.current.onBattleChange({
          encounterId: initialBattle.encounterId,
          status: this.status,
          heroes: this.heroes.map(snapshotCombatant),
          creatures: this.creatures.map(snapshotCombatant),
        })
      }

      shutdown() {
        this.scale.off('resize', this.resizeHandler)
      }
    }

    const game = new Phaser.Game({
      type: Phaser.AUTO,
      parent: host,
      width: Math.max(host.clientWidth, 320),
      height: COMBAT_HEIGHT,
      backgroundColor: '#121b2e',
      scene: BattleScene,
    })
    const resizeObserver = new ResizeObserver(() => {
      game.scale.resize(Math.max(host.clientWidth, 320), COMBAT_HEIGHT)
    })

    resizeObserver.observe(host)
    return () => {
      resizeObserver.disconnect()
      game.destroy(true)
    }
  }, [battle.encounterId])

  return <div className="combat-scene" ref={hostRef} aria-label="Automatic battle in progress" />
}

export default CombatScene
