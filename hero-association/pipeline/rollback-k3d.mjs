export async function restorePreviousImages(changed, previous, target, getImage, setImage) {
  const errors = []
  for (const component of [...changed].reverse()) {
    try {
      let image = await getImage(component)
      if (image === target[component]) {
        await setImage(component, previous[component])
        image = await getImage(component)
      }
      if (image !== previous[component]) {
        throw new Error(`image is ${image}, expected ${previous[component]}`)
      }
    } catch (error) {
      errors.push(`${component}: ${error.message}`)
    }
  }
  return errors
}
