# Final design integration

Source of truth: validated Claude Design ZYVIOTV Design System.

## Locked visual rules

- base/canvas: #050506 / #0A0A0C
- surfaces: #121215, #1A1A1F, #24242A, #2E2E35
- brand red: #E0102F
- bright live/accent red: #FF3B52
- primary text: #F4F4F6
- secondary text: #A9A9B3
- tertiary text: #7A7A84
- **white = focus**
- **red = active/selected/live**
- TV focus scale: 1.06
- TV is a dedicated input/device profile, never inferred from width alone
- no permanent blur/shader animation
- only transform/opacity animations for normal motion
- 4 dp spacing grid

## Integration order

1. foundations: colors, typography, surfaces, focus
2. authentication
3. global search
4. live TV
5. EPG
6. movies + movie details
7. series + series details + seasons/episodes
8. account/settings
9. final responsive and physical-device visual QA

The design integration must not replace real provider/data contracts with placeholders. Existing functional work remains the data source.
