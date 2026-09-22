import Phaser from 'phaser'
import { useEffect, useRef } from 'react'

const COMBAT_HEIGHT = 330

function cooldownFraction(combatant, spell, currentTimeMilliseconds) {
  const nextCastAt = combatant.nextSpellCastAt?.[spell.id]
  if (!nextCastAt || nextCastAt <= currentTimeMilliseconds) {
    return 0
  }
  return Math.min(1, (nextCastAt - currentTimeMilliseconds) / spell.cooldown)
}

function CombatScene({ battle }) {
  const hostRef = useRef(null)
  const sceneRef = useRef(null)
  const battleRef = useRef(battle)

  useEffect(() => {
    battleRef.current = battle
    sceneRef.current?.updateBattle(battle)
  }, [battle])

  useEffect(() => {
    const host = hostRef.current
    if (!host) {
      return undefined
    }

    class BattleScene extends Phaser.Scene {
      constructor() {
        super('battle')
        this.resizeHandler = this.layout.bind(this)
        this.eventQueue = []
        this.isReplayingEvents = false
        this.lastEventSequence = -1
      }

      create() {
        this.field = this.add.graphics()
        this.add.text(22, 20, 'DAWNWATCH PARTY', labelStyle()).setOrigin(0, 0)
        this.enemyLabel = this.add.text(0, 20, 'CREATURES', labelStyle()).setOrigin(1, 0)
        this.createCombatants(battleRef.current)
        this.lastEventSequence = latestEventSequence(battleRef.current.events)
        this.latestBattle = battleRef.current
        this.scale.on('resize', this.resizeHandler)
        this.layout()
        sceneRef.current = this
      }

      createCombatants(snapshot) {
        this.heroes = snapshot.heroes.map(copyCombatant)
        this.creatures = snapshot.creatures.map(copyCombatant)
        this.combatantsById = new Map()
        this.heroes.forEach((hero, index) => this.createCombatant(hero, index, 'heroes'))
        this.creatures.forEach((creature, index) => this.createCombatant(creature, index, 'creatures'))
      }

      createCombatant(combatant, index, side) {
        const resourceTextX = side === 'heroes' ? -39 : 47
        const resourceTextOrigin = side === 'heroes' ? 1 : 0
        const resourceValueX = side === 'heroes' ? -34 : 42
        const resourceValueOrigin = side === 'heroes' ? 0 : 1
        const container = this.add.container(0, 0)
        const avatar = this.add.circle(0, 0, 26, combatant.color, 1)
        const ring = this.add.circle(0, 0, 26).setStrokeStyle(2, 0xf3e9ca, 0.55)
        const initial = this.add.text(0, 0, combatant.name.slice(0, 1), avatarStyle(20)).setOrigin(0.5)
        const name = this.add.text(0, 44, combatant.name, nameStyle()).setOrigin(0.5)
        const role = this.add.text(0, 59, combatant.magicLevel ? `${combatant.role} · ML ${combatant.magicLevel}` : combatant.role ?? 'Creature', roleStyle()).setOrigin(0.5)
        const elements = [avatar, ring, initial, name, role]
        const healthValue = this.add.rectangle(resourceValueX, 74, 76, 6, 0x70bf8b, 1).setOrigin(resourceValueOrigin, 0.5)
        const healthText = this.add.text(resourceTextX, 74, '', resourceStyle()).setOrigin(resourceTextOrigin, 0.5)
        const manaValue = this.add.rectangle(resourceValueX, 88, 76, 6, 0x5d92df, 1).setOrigin(resourceValueOrigin, 0.5)
        const manaText = this.add.text(resourceTextX, 88, '', resourceStyle()).setOrigin(resourceTextOrigin, 0.5)
        elements.push(
          this.add.rectangle(4, 74, 78, 8, 0x090e19, 1).setStrokeStyle(1, 0x56607a, 0.6),
          healthValue,
          healthText,
          this.add.rectangle(4, 88, 78, 8, 0x090e19, 1).setStrokeStyle(1, 0x56607a, 0.6),
          manaValue,
          manaText,
        )

        if (combatant.level) {
          elements.push(
            this.add.circle(-23, -23, 11, 0x283b61, 1).setStrokeStyle(1, 0xd9b560, 0.9),
            this.add.text(-23, -23, combatant.level, levelStyle()).setOrigin(0.5),
          )
        }

        const cooldownOverlays = []
        combatant.spells?.forEach((spell, spellIndex) => {
          const x = -13 + (spellIndex * 26)
          const cooldownOverlay = this.add.graphics().setPosition(x, 104)
          elements.push(
            this.add.rectangle(x, 104, 22, 22, 0x2a2542, 1).setStrokeStyle(1, 0x8a70bd, 0.95),
            this.add.text(x, 104, spell.symbol, spellStyle()).setOrigin(0.5),
            cooldownOverlay,
          )
          cooldownOverlays.push({ spell, overlay: cooldownOverlay })
        })

        if (combatant.role) {
          combatant.runes.forEach((rune, runeIndex) => {
            const x = -32 + (runeIndex * 16)
            elements.push(this.add.rectangle(x, 128, 14, 14, rune ? 0x2b2b36 : 0x121a2b, 1).setStrokeStyle(1, rune ? 0xdcb662 : 0x59667e, 0.9))
            if (rune) {
              elements.push(this.add.text(x, 128, rune.symbol, runeStyle()).setOrigin(0.5))
            }
          })
        }

        container.add(elements)
        combatant.view = { container, avatar, ring, initial, name, role, healthValue, healthText, manaValue, manaText, cooldownOverlays, side, index }
        this.combatantsById.set(combatant.id, combatant)
        this.refreshCombatant(combatant)
      }

      updateBattle(snapshot) {
        this.latestBattle = snapshot
        const newEvents = (snapshot.events ?? [])
          .filter((event) => event.sequenceNumber > this.lastEventSequence)
          .sort((left, right) => left.sequenceNumber - right.sequenceNumber)

        if (newEvents.length === 0) {
          if (!this.isReplayingEvents) {
            this.applySnapshot(snapshot)
          }
          return
        }

        this.lastEventSequence = newEvents[newEvents.length - 1].sequenceNumber
        this.eventQueue.push(...newEvents)
        this.replayNextEvent()
      }

      replayNextEvent() {
        const event = this.eventQueue.shift()
        if (!event) {
          this.isReplayingEvents = false
          this.applySnapshot(this.latestBattle)
          return
        }

        this.isReplayingEvents = true
        this.renderEvent(event)
        const delay = event.action === 'RECOVERY' ? 90 : event.hits.length > 1 ? 260 : 170
        this.time.delayedCall(delay, () => this.replayNextEvent())
      }

      renderEvent(event) {
        const actor = this.combatantsById.get(event.actorId)
        if (!actor) {
          return
        }

        this.pulseCombatant(actor)
        if (event.action === 'RECOVERY') {
          this.applyRecovery(actor, event)
          return
        }

        if (event.manaSpent > 0) {
          actor.currentMana = Math.max(0, actor.currentMana - event.manaSpent)
          this.refreshCombatant(actor)
          this.showFloatingText(actor, `-${event.manaSpent} MP`, '#8dc4ff', -28)
        }
        if (event.action !== 'BASIC_ATTACK') {
          this.showFloatingText(actor, eventLabel(event.action), '#caa5ff', -46)
        }
        event.hits.forEach((hit, hitIndex) => this.applyHit(event, hit, hitIndex))
      }

      applyRecovery(actor, event) {
        actor.currentHealth = Math.min(actor.maxHealth, actor.currentHealth + event.healthRecovered)
        actor.currentMana = Math.min(actor.maxMana, actor.currentMana + event.manaRecovered)
        this.refreshCombatant(actor)
        if (event.healthRecovered > 0) {
          this.showFloatingText(actor, `+${event.healthRecovered} HP`, '#9ce3ad', -31)
        }
        if (event.manaRecovered > 0) {
          this.showFloatingText(actor, `+${event.manaRecovered} MP`, '#8dc4ff', -47)
        }
      }

      applyHit(event, hit, hitIndex) {
        const target = this.combatantsById.get(hit.targetId)
        if (!target) {
          return
        }

        target.currentHealth = Math.max(0, target.currentHealth - hit.damage)
        if (hit.defeated) {
          target.alive = false
        }
        this.refreshCombatant(target)
        this.showHit(target, hit, hitIndex, event)
      }

      showHit(target, hit, hitIndex, event) {
        const isMagic = event.action === 'FIRE_BALL' || event.action === 'LIGHTNING_RAIL'
        const lane = (event.sequenceNumber + hitIndex) % 3
        const xOffset = (lane - 1) * 18
        const text = hit.critical ? `${hit.damage}!` : `${hit.damage}`
        const color = hit.critical ? '#fff1a8' : isMagic ? '#d8aeff' : '#f1ca72'
        this.showFloatingText(target, text, color, -32, xOffset, hit.critical ? 18 : 14)
        if (hit.critical) {
          this.shakeCombatant(target)
        }
        if (hit.defeated) {
          this.showFloatingText(target, 'Defeated', '#d9a8a8', -53)
        }
      }

      showFloatingText(combatant, text, color, yOffset, xOffset = 0, fontSize = 12) {
        const popup = this.add.text(
          combatant.view.container.x + xOffset,
          combatant.view.container.y + yOffset,
          text,
          { color, fontFamily: 'system-ui, sans-serif', fontSize: `${fontSize}px`, fontStyle: 'bold' },
        ).setOrigin(0.5).setDepth(10)
        this.tweens.add({
          targets: popup,
          x: popup.x + (xOffset === 0 ? 0 : Math.sign(xOffset) * 10),
          y: popup.y - 24,
          alpha: 0,
          duration: 680,
          ease: 'Cubic.Out',
          onComplete: () => popup.destroy(),
        })
      }

      pulseCombatant(combatant) {
        this.tweens.add({
          targets: combatant.view.container,
          scaleX: 1.07,
          scaleY: 1.07,
          duration: 75,
          yoyo: true,
          ease: 'Sine.Out',
        })
      }

      shakeCombatant(combatant) {
        const initialX = combatant.view.container.x
        this.tweens.add({
          targets: combatant.view.container,
          x: initialX + 4,
          duration: 45,
          yoyo: true,
          repeat: 3,
          ease: 'Sine.InOut',
          onComplete: () => combatant.view.container.setX(initialX),
        })
      }

      applySnapshot(snapshot) {
        const combatantsById = new Map([...snapshot.heroes, ...snapshot.creatures].map((combatant) => [combatant.id, combatant]))
        this.combatantsById.forEach((combatant, id) => {
          const incoming = combatantsById.get(id)
          if (!incoming) {
            return
          }
          combatant.currentHealth = incoming.currentHealth
          combatant.currentMana = incoming.currentMana
          combatant.alive = incoming.alive
          combatant.nextSpellCastAt = incoming.nextSpellCastAt
          this.refreshCombatant(combatant, snapshot.currentTimeMilliseconds)
        })
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
          const x = combatants.length === 1 ? (left + right) / 2 : left + (formationIndex * spacing)
          combatant.view.container.setPosition(x, height * 0.5)
        })
      }

      refreshCombatant(combatant, currentTimeMilliseconds = this.latestBattle?.currentTimeMilliseconds ?? 0) {
        const healthPercentage = combatant.maxHealth === 0 ? 0 : combatant.currentHealth / combatant.maxHealth
        combatant.view.healthValue.displayWidth = 76 * healthPercentage
        combatant.view.healthValue.setFillStyle(healthPercentage > 0.35 ? 0x70bf8b : 0xd99462)
        combatant.view.healthText.setText(combatant.currentHealth)
        const manaPercentage = combatant.maxMana === 0 ? 0 : combatant.currentMana / combatant.maxMana
        combatant.view.manaValue.displayWidth = 76 * manaPercentage
        combatant.view.manaText.setText(combatant.currentMana)
        combatant.view.cooldownOverlays.forEach(({ spell, overlay }) => {
          overlay.clear()
          drawSpellCooldown(overlay, cooldownFraction(combatant, spell, currentTimeMilliseconds))
        })
        const alpha = combatant.alive ? 1 : 0.32
        combatant.view.avatar.setAlpha(alpha)
        combatant.view.ring.setAlpha(combatant.alive ? 0.55 : 0.2)
        combatant.view.initial.setAlpha(alpha)
        combatant.view.name.setAlpha(alpha)
        combatant.view.role.setAlpha(alpha)
      }

      shutdown() {
        this.scale.off('resize', this.resizeHandler)
        if (sceneRef.current === this) {
          sceneRef.current = null
        }
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
    const resizeObserver = new ResizeObserver(() => game.scale.resize(Math.max(host.clientWidth, 320), COMBAT_HEIGHT))
    resizeObserver.observe(host)
    return () => {
      resizeObserver.disconnect()
      sceneRef.current = null
      game.destroy(true)
    }
  }, [])

  return <div className="combat-scene" ref={hostRef} aria-label="Server-synchronized automatic battle" />
}

function copyCombatant(combatant) {
  return { ...combatant, nextSpellCastAt: { ...combatant.nextSpellCastAt } }
}

function latestEventSequence(events) {
  return events.reduce((latest, event) => Math.max(latest, event.sequenceNumber), -1)
}

function eventLabel(action) {
  return action.toLowerCase().split('_').map((word) => `${word[0].toUpperCase()}${word.slice(1)}`).join(' ')
}

function labelStyle() {
  return { color: '#dce4f5', fontFamily: 'system-ui, sans-serif', fontSize: '10px', fontStyle: 'bold', letterSpacing: 1 }
}

function avatarStyle(fontSize) {
  return { color: '#fff8e7', fontFamily: 'Georgia, serif', fontSize: `${fontSize}px`, fontStyle: 'bold' }
}

function nameStyle() {
  return { color: '#e6e9f0', fontFamily: 'system-ui, sans-serif', fontSize: '11px', fontStyle: 'bold' }
}

function roleStyle() {
  return { color: '#94a0b9', fontFamily: 'system-ui, sans-serif', fontSize: '9px' }
}

function resourceStyle() {
  return { color: '#b9c4d8', fontFamily: 'system-ui, sans-serif', fontSize: '9px', fontStyle: 'bold' }
}

function levelStyle() {
  return { color: '#f7e5ae', fontFamily: 'system-ui, sans-serif', fontSize: '10px', fontStyle: 'bold' }
}

function spellStyle() {
  return { color: '#f1dc9c', fontFamily: 'system-ui, sans-serif', fontSize: '14px', fontStyle: 'bold' }
}

function runeStyle() {
  return { color: '#f2dfab', fontFamily: 'system-ui, sans-serif', fontSize: '9px', fontStyle: 'bold' }
}

function drawSpellCooldown(overlay, remaining) {
  if (remaining <= 0) {
    return
  }
  const startAngle = -Math.PI / 2
  const endAngle = startAngle - (Math.min(remaining, 0.9999) * Math.PI * 2)
  overlay.fillStyle(0x080d18, 0.76)
  overlay.beginPath()
  overlay.moveTo(0, 0)
  overlay.arc(0, 0, 10, startAngle, endAngle, true)
  overlay.closePath()
  overlay.fillPath()
}

export default CombatScene
