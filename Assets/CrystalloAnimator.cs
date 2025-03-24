using KinematicCharacterControllerNamespace;
using UnityEngine;

public class CrystalloAnimator : MonoBehaviour
{
    [SerializeField] TinyWizCharacterController characterController;
    [SerializeField] KinematicCharacterConfig motor;
    [SerializeField] Animator Animator;
    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {
        
    }

    // Update is called once per frame
    void Update()
    {
        if (motor.IsOnGround() == false)
        {
            Animator.SetBool("Jump", true);
        }
        else
        {
            Animator.SetBool("Jump", false);
        }

        if (motor.IsMoving() == true)
        {
            Animator.SetFloat("ForwardMotion", 1);
        }
        else
        {
            Animator.SetFloat("ForwardMotion", 0);
        }
    }
}
