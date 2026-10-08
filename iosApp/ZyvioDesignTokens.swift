import SwiftUI

/// Claude Design D6 foundations. Existing screens can adopt these constants
/// incrementally without changing player behavior or navigation semantics.
enum ZyvioDesign {
    enum Palette {
        static let base = Color(red: 5 / 255, green: 5 / 255, blue: 6 / 255)
        static let canvas = Color(red: 10 / 255, green: 10 / 255, blue: 12 / 255)
        static let surface1 = Color(red: 18 / 255, green: 18 / 255, blue: 21 / 255)
        static let surface2 = Color(red: 26 / 255, green: 26 / 255, blue: 31 / 255)
        static let surface3 = Color(red: 36 / 255, green: 36 / 255, blue: 42 / 255)
        static let brand = Color(red: 224 / 255, green: 16 / 255, blue: 47 / 255)
        static let pressed = Color(red: 179 / 255, green: 12 / 255, blue: 37 / 255)
        static let text = Color(red: 244 / 255, green: 244 / 255, blue: 246 / 255)
        static let secondary = Color(red: 169 / 255, green: 169 / 255, blue: 179 / 255)
    }

    enum Space {
        static let s1: CGFloat = 4
        static let s2: CGFloat = 8
        static let s3: CGFloat = 12
        static let s4: CGFloat = 16
        static let s5: CGFloat = 20
        static let s6: CGFloat = 24
        static let s8: CGFloat = 32
        static let s10: CGFloat = 40
        static let s12: CGFloat = 48
        static let s16: CGFloat = 64
        static let s24: CGFloat = 96
    }

    enum Radius {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 12
        static let lg: CGFloat = 16
        static let xl: CGFloat = 24
    }

    enum Motion {
        static let press: Double = 0.09
        static let focus: Double = 0.15
        static let rowScroll: Double = 0.22
        static let page: Double = 0.26
    }
}
